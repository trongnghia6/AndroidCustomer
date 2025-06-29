import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': '*',
  'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
  'Access-Control-Allow-Content-Type': 'application/json, application/x-www-form-urlencoded',
}

interface FCMRequest {
  token?: string
  user_id?: string
  title: string
  body: string
  data?: Record<string, string>
}

serve(async (req) => {
  // ✅ BYPASS: Allow all requests without auth check
  console.log(`🔓 === NO AUTH CHECK - ALLOWING ALL REQUESTS ===`)
  
  // Allow all CORS requests
  if (req.method === 'OPTIONS') {
    console.log(`✅ OPTIONS request - returning CORS headers`)
    return new Response('ok', { headers: corsHeaders })
  }

  // Detailed logging for debugging 401 issues
  console.log(`🔔 === PUSH NOTIFICATION REQUEST ===`)
  console.log(`Method: ${req.method}`)
  console.log(`URL: ${req.url}`)
  console.log(`User-Agent: ${req.headers.get('user-agent') || 'unknown'}`)
  console.log(`Content-Type: ${req.headers.get('content-type') || 'unknown'}`)
  console.log(`Origin: ${req.headers.get('origin') || 'unknown'}`)
  
  try {
    console.log(`📖 Reading request body...`)
    const requestBody = await req.text()
    console.log(`Raw body length: ${requestBody.length}`)
    console.log(`Raw body: ${requestBody}`)
    
    if (!requestBody || requestBody.trim() === '') {
      console.error(`❌ Empty request body`)
      return new Response(JSON.stringify({ 
        error: 'Empty request body',
        success: false,
        timestamp: new Date().toISOString()
      }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      })
    }
    
    // ✅ HANDLE BOTH JSON AND FORM DATA
    const contentType = req.headers.get('content-type') || ''
    console.log(`🔄 Content-Type: ${contentType}`)
    
    let parsedData: FCMRequest
    
    if (contentType.includes('application/x-www-form-urlencoded')) {
      console.log(`🔄 Parsing form data...`)
      try {
        const formData = new URLSearchParams(requestBody)
        
        // Convert form data to FCMRequest object
        parsedData = {
          token: formData.get('token') || undefined,
          user_id: formData.get('user_id') || undefined,
          title: formData.get('title') || '',
          body: formData.get('body') || '',
          data: {}
        }
        
        // Parse data field if it exists (should be JSON string in form data)
        const dataField = formData.get('data')
        if (dataField) {
          try {
            parsedData.data = JSON.parse(dataField)
          } catch (e) {
            console.log(`⚠️ Could not parse data field as JSON, using as string`)
            parsedData.data = { raw: dataField }
          }
        }
        
        console.log(`📝 Parsed form data:`, JSON.stringify(parsedData))
      } catch (e) {
        console.error(`❌ Failed to parse form data:`, e)
        throw new Error(`Failed to parse form data: ${e.message}`)
      }
    } else if (contentType.includes('application/json') || contentType === '' || !contentType) {
      console.log(`🔄 Parsing JSON...`)
      try {
        parsedData = JSON.parse(requestBody)
      } catch (e) {
        console.error(`❌ Failed to parse JSON:`, e)
        throw new Error(`Failed to parse JSON: ${e.message}`)
      }
    } else {
      console.log(`⚠️ Unknown content-type: ${contentType}, attempting JSON parse...`)
      try {
        parsedData = JSON.parse(requestBody)
      } catch (e) {
        console.error(`❌ Failed to parse unknown content-type as JSON:`, e)
        throw new Error(`Unsupported content-type: ${contentType}. Expected application/json or application/x-www-form-urlencoded`)
      }
    }
    
    const { token, user_id, title, body, data }: FCMRequest = parsedData
    
    console.log(`📝 Parsed request (Content-Type: ${contentType}):`)
    console.log(`  - token: ${token ? token.substring(0, 10) + '...' : 'null'}`)
    console.log(`  - user_id: ${user_id || 'null'}`)
    console.log(`  - title: ${title}`)
    console.log(`  - body: ${body}`)
    console.log(`  - data: ${JSON.stringify(data || {})}`)
    console.log(`  - parsing_method: ${contentType.includes('application/x-www-form-urlencoded') ? 'form-data' : 'json'}`)

    // Validate input
    if ((!token && !user_id) || !title || !body) {
      console.error(`❌ Validation failed: Missing required fields`)
      throw new Error('Missing required fields: (token OR user_id), title, body')
    }
    
    console.log(`✅ Validation passed`)

    // Get tokens to send to
    let tokensToSend: string[] = []
    
    if (token) {
      // Direct token provided
      console.log(`🎯 Using direct token: ${token.substring(0, 10)}...`)
      tokensToSend = [token]
    } else if (user_id) {
      // Get tokens from database for user_id
      console.log(`🔍 Fetching tokens for user_id: ${user_id}`)
      
      const supabaseUrl = Deno.env.get('SUPABASE_URL')!
      const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
      console.log(`🔧 Supabase URL: ${supabaseUrl}`)
      console.log(`🔧 Service key: ${supabaseServiceRoleKey ? 'CONFIGURED' : 'NOT CONFIGURED'}`)
      
      if (!supabaseServiceRoleKey) {
        console.error(`❌ SUPABASE_SERVICE_ROLE_KEY not configured`)
        throw new Error('SUPABASE_SERVICE_ROLE_KEY not configured')
      }
      
      const supabase = createClient(supabaseUrl, supabaseServiceRoleKey)
      
      console.log(`🔍 Querying user_push_tokens table...`)
      const { data: tokenData, error: tokenError } = await supabase
        .from('user_push_tokens')
        .select('token')
        .eq('user_id', user_id)

      if (tokenError) {
        console.error('❌ Error fetching tokens:', tokenError)
        throw new Error(`Failed to fetch tokens: ${tokenError.message}`)
      }

      console.log(`📱 Token query result: ${JSON.stringify(tokenData)}`)

      if (!tokenData || tokenData.length === 0) {
        console.log(`⚠️ No FCM tokens found for user_id: ${user_id}`)
        return new Response(JSON.stringify({ 
          success: true, 
          message: 'No tokens found for user',
          sentCount: 0,
          user_id: user_id,
          timestamp: new Date().toISOString()
        }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        })
      }

      tokensToSend = tokenData.map(row => row.token)
      console.log(`📱 Found ${tokensToSend.length} FCM tokens for user`)
    }

    // ✅ RESTORED: Get Firebase service account key from environment
    console.log(`🔥 Checking Firebase configuration...`)
    const serviceAccountKey = Deno.env.get('FIREBASE_SERVICE_ACCOUNT_KEY')
    if (!serviceAccountKey) {
      console.error(`❌ Firebase service account key not configured`)
      // Still try to return success to avoid breaking triggers
      return new Response(JSON.stringify({ 
        success: false, 
        message: 'Firebase service account not configured - no push sent',
        user_id: user_id || 'direct_token',
        timestamp: new Date().toISOString()
      }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      })
    }
    console.log(`✅ Firebase service account key found`)

    // Parse service account
    const serviceAccount = JSON.parse(serviceAccountKey)
    console.log(`🔧 Firebase project: ${serviceAccount.project_id}`)
    
    // Get access token
    console.log(`🔑 Getting Firebase access token...`)
    const accessToken = await getAccessToken(serviceAccount)
    console.log(`✅ Firebase access token obtained`)
    
    // ✅ RESTORED: Send to all tokens
    console.log(`🚀 Starting FCM sending process...`)
    console.log(`📡 Will send to ${tokensToSend.length} tokens`)
    
    const results = []
    let successCount = 0
    
    for (const fcmToken of tokensToSend) {
      console.log(`📤 Sending to token: ${fcmToken.substring(0, 10)}...`)
      try {
        // Prepare FCM v1 message
        const fcmMessage = {
          message: {
            token: fcmToken,
            notification: {
              title: title,
              body: body
            },
            data: data || {},
            android: {
              notification: {
                click_action: "FLUTTER_NOTIFICATION_CLICK",
                sound: "default"
              }
            }
          }
        }

        // Send to FCM
        const projectId = serviceAccount.project_id
        const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(fcmMessage)
        })

        if (!response.ok) {
          const errorText = await response.text()
          console.error(`FCM Error for token ${fcmToken.substring(0, 10)}...: ${response.status} - ${errorText}`)
          results.push({ token: fcmToken.substring(0, 10) + '...', success: false, error: errorText })
        } else {
          const result = await response.json()
          console.log(`FCM Success for token ${fcmToken.substring(0, 10)}...: ${result.name}`)
          results.push({ token: fcmToken.substring(0, 10) + '...', success: true, messageId: result.name })
          successCount++
        }
      } catch (error) {
        console.error(`Error sending to token ${fcmToken.substring(0, 10)}...:`, error)
        results.push({ token: fcmToken.substring(0, 10) + '...', success: false, error: error.message })
      }
    }
    
    const finalResult = {
      success: successCount > 0,
      totalTokens: tokensToSend.length,
      successCount: successCount,
      failedCount: tokensToSend.length - successCount,
      results: results,
      user_id: user_id || 'direct_token',
      timestamp: new Date().toISOString()
    }
    
    console.log(`🎉 === FINAL RESULT ===`)
    console.log(`Success: ${finalResult.success}`)
    console.log(`Total tokens: ${finalResult.totalTokens}`)
    console.log(`Success count: ${finalResult.successCount}`)
    console.log(`Failed count: ${finalResult.failedCount}`)
    console.log(`User ID: ${finalResult.user_id}`)
    console.log(`======================`)
    
    return new Response(JSON.stringify(finalResult), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })

  } catch (error) {
    console.error('❌ === PUSH NOTIFICATION ERROR ===')
    console.error('Error type:', error.constructor.name)
    console.error('Error message:', error.message)
    console.error('Error stack:', error.stack)
    console.error('===============================')
    
    return new Response(JSON.stringify({ 
      error: error.message,
      errorType: error.constructor.name,
      success: false,
      timestamp: new Date().toISOString(),
      stack: error.stack
    }), {
      status: 200, // ✅ Return 200 instead of 400 to avoid triggering auth
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })
  }
})

async function getAccessToken(serviceAccount: any): Promise<string> {
  const jwtHeader = {
    alg: "RS256",
    typ: "JWT"
  }

  const now = Math.floor(Date.now() / 1000)
  const jwtPayload = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600
  }

  // Create JWT (simplified - in production use proper JWT library)
  const jwtToken = await createJWT(jwtHeader, jwtPayload, serviceAccount.private_key)

  // Exchange JWT for access token
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwtToken}`
  })

  if (!tokenResponse.ok) {
    throw new Error(`Failed to get access token: ${tokenResponse.status}`)
  }

  const tokenData = await tokenResponse.json()
  return tokenData.access_token
}

async function createJWT(header: any, payload: any, privateKey: string): Promise<string> {
  const encoder = new TextEncoder()
  
  const headerB64 = btoa(JSON.stringify(header)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  const payloadB64 = btoa(JSON.stringify(payload)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  
  const data = `${headerB64}.${payloadB64}`
  
  // Import private key
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(privateKey),
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: "SHA-256"
    },
    false,
    ["sign"]
  )
  
  // Sign
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    encoder.encode(data)
  )
  
  const signatureB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  
  return `${data}.${signatureB64}`
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s/g, '')
  
  const binary = atob(b64)
  const bytes = new Uint8Array(binary.length)
  
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  
  return bytes.buffer
}

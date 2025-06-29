import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

interface MessageNotificationRequest {
  receiver_id: string
  sender_id: string
  sender_name: string
  message_preview: string
  conversation_id?: string
  message_id?: string
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { 
      receiver_id, 
      sender_id, 
      sender_name, 
      message_preview, 
      conversation_id,
      message_id 
    }: MessageNotificationRequest = await req.json()

    // Validate input
    if (!receiver_id || !sender_id || !sender_name || !message_preview) {
      throw new Error('Missing required fields: receiver_id, sender_id, sender_name, message_preview')
    }

    // Tạo notification content
    const title = `Tin nhắn mới từ ${sender_name} 💬`
    const body = message_preview.length > 50 
      ? `${message_preview.substring(0, 47)}...`
      : message_preview

    // Khởi tạo Supabase client
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
    const supabase = createClient(supabaseUrl, supabaseServiceRoleKey)

    // Lưu notification vào database
    const { error: insertError } = await supabase
      .from('notifications')
      .insert({
        user_id: receiver_id,
        title: title,
        body: body,
        type: 'message',
        data: {
          sender_id: sender_id,
          sender_name: sender_name,
          conversation_id: conversation_id || "",
          message_id: message_id || "",
          navigate_to: "chat_detail"
        }
      })

    if (insertError) {
      throw new Error(`Database insert failed: ${insertError.message}`)
    }

    // Lấy FCM tokens của receiver
    const { data: tokens, error: tokenError } = await supabase
      .from('user_push_tokens')
      .select('token')
      .eq('user_id', receiver_id)

    if (tokenError) {
      console.error('Error fetching tokens:', tokenError)
    }

    // Gửi push notification nếu có tokens
    if (tokens && tokens.length > 0) {
      for (const tokenRow of tokens) {
        try {
          // Gọi FCM push notification function
          await fetch(`${supabaseUrl}/functions/v1/send-push`, {
            method: 'POST',
            headers: {
              'Authorization': `Bearer ${supabaseServiceRoleKey}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({
              token: tokenRow.token,
              title: title,
              body: body,
              data: {
                sender_id: sender_id,
                sender_name: sender_name,
                conversation_id: conversation_id || "",
                message_id: message_id || "",
                type: 'message'
              }
            })
          })
        } catch (pushError) {
          console.error('Error sending push notification:', pushError)
        }
      }
    }

    return new Response(JSON.stringify({ 
      success: true, 
      message: 'Message notification sent successfully',
      notification_saved: true,
      push_sent: tokens?.length || 0,
      receiver_id: receiver_id,
      message_id: message_id || ""
    }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })

  } catch (error) {
    console.error('Message notification error:', error)
    return new Response(JSON.stringify({ 
      error: error.message,
      success: false 
    }), {
      status: 400,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })
  }
}) 
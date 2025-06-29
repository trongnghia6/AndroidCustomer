import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

interface BookingNotificationRequest {
  user_id: string
  booking_id: string
  action: string
  service_name?: string
  description?: string
  role?: 'customer' | 'provider'
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { user_id, booking_id, action, service_name, description, role }: BookingNotificationRequest = await req.json()

    // Validate input
    if (!user_id || !booking_id || !action) {
      throw new Error('Missing required fields: user_id, booking_id, action')
    }

    // Tạo notification title và body based on action và role
    let title = ""
    let body = ""
    
    const isProvider = role === 'provider'
    
    switch (action) {
      case "created":
        title = isProvider ? "Đơn hàng mới 🔔" : "Đặt lịch mới 📅"
        body = isProvider 
          ? `Bạn có đơn hàng mới. Mã booking: #${booking_id}`
          : `Bạn đã đặt lịch thành công. Mã booking: #${booking_id}`
        break
      case "confirmed":
        title = isProvider ? "Đã xác nhận đơn hàng ✅" : "Lịch hẹn đã xác nhận ✅"
        body = isProvider 
          ? `Bạn đã xác nhận đơn hàng #${booking_id}.`
          : `Lịch hẹn #${booking_id} đã được xác nhận bởi nhà cung cấp dịch vụ.`
        break
      case "cancelled":
        title = isProvider ? "Đơn hàng đã hủy ❌" : "Lịch hẹn đã hủy ❌"
        body = isProvider 
          ? `Đơn hàng #${booking_id} đã bị hủy bởi khách hàng.`
          : `Lịch hẹn #${booking_id} đã bị hủy. Vui lòng liên hệ để biết thêm chi tiết.`
        break
      case "completed":
        title = isProvider ? "Đã hoàn thành dịch vụ ✅" : "Dịch vụ hoàn thành 🎉"
        body = isProvider 
          ? `Bạn đã hoàn thành dịch vụ cho đơn hàng #${booking_id}.`
          : `Dịch vụ trong lịch hẹn #${booking_id} đã hoàn thành. Cảm ơn bạn đã sử dụng dịch vụ!`
        break
      case "updated":
        title = isProvider ? "Cập nhật đơn hàng ✏️" : "Cập nhật lịch hẹn ✏️"
        body = isProvider 
          ? `Đơn hàng #${booking_id} đã được cập nhật thông tin.`
          : `Lịch hẹn #${booking_id} đã được cập nhật thông tin.`
        break
      default:
        title = isProvider ? "Cập nhật đơn hàng 📋" : "Cập nhật booking 📋"
        body = `Booking #${booking_id} có cập nhật mới.`
    }

    // Thêm description/service name nếu có
    if (description) {
      body += `\nMô tả: ${description}`
    } else if (service_name) {
      body += `\nDịch vụ: ${service_name}`
    }

    // Khởi tạo Supabase client
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
    const supabase = createClient(supabaseUrl, supabaseServiceRoleKey)

    // Lưu notification vào database
    const { error: insertError } = await supabase
      .from('notifications')
      .insert({
        user_id: user_id,
        title: title,
        body: body,
        type: 'booking_update',
        data: {
          booking_id: booking_id,
          action: action,
          service_name: service_name || "",
          description: description || "",
          role: role || "customer",
          navigate_to: "booking_detail"
        }
      })

    if (insertError) {
      throw new Error(`Database insert failed: ${insertError.message}`)
    }

    // Lấy FCM tokens của user
    const { data: tokens, error: tokenError } = await supabase
      .from('user_push_tokens')
      .select('token')
      .eq('user_id', user_id)

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
                booking_id: booking_id,
                action: action,
                role: role || "customer",
                type: 'booking_update'
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
      message: 'Booking notification sent successfully',
      notification_saved: true,
      push_sent: tokens?.length || 0
    }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })

  } catch (error) {
    console.error('Booking notification error:', error)
    return new Response(JSON.stringify({ 
      error: error.message,
      success: false 
    }), {
      status: 400,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    })
  }
}) 
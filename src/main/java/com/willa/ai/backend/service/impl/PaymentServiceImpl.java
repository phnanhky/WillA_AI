package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.entity.Payment;
import com.willa.ai.backend.entity.Plan;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.enums.PaymentStatus;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.PaymentRepository;
import com.willa.ai.backend.repository.PlanRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.PaymentService;
import com.willa.ai.backend.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PayOS payOS;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

    @Value("${payos.return-url}")
    private String baseReturnUrl;

    @Value("${payos.cancel-url}")
    private String baseCancelUrl;

    @Override
    @Transactional
    public CheckoutResponseData createPaymentLink(String userEmail, Long planId) {
        try {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

            Plan plan = planRepository.findById(planId)
                    .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + planId));

            Long amount = plan.getPrice().longValue();
            Long orderCode = System.currentTimeMillis() / 1000;
            String originalName = plan.getName();
            // PayOS không nhận các ký tự đặc biệt, dễ làm sai lệch signature khi check
            String safeName = originalName.replaceAll("[^a-zA-Z0-9 ]", ""); 
            String description = "WillA AI Plan";

            // Lưu lịch sử thanh toán vào database với trạng thái PENDING
            Payment payment = Payment.builder()
                    .orderCode(orderCode)
                    .amount(amount)
                    .description(description)
                    .status(PaymentStatus.PENDING)
                    .user(user)
                    .plan(plan)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            paymentRepository.save(payment);

            String returnUrl = baseReturnUrl + "?orderCode=" + orderCode;
            String cancelUrl = baseCancelUrl + "?orderCode=" + orderCode;

            // Xử lý trực tiếp với API PayOS để bỏ qua lỗi SDK (Signature mismatch do expiredAt)
            String dataStr = "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl;
            
            Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSHA256.init(secretKeySpec);
            byte[] hashBytes = hmacSHA256.doFinal(dataStr.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String signature = hexString.toString();

            String rawJson = String.format("{\"orderCode\": %d, \"amount\": %d, \"description\": \"%s\", \"cancelUrl\": \"%s\", \"returnUrl\": \"%s\", \"signature\": \"%s\"}",
                    orderCode, amount, description, cancelUrl, returnUrl, signature);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api-merchant.payos.vn/v2/payment-requests"))
                    .header("Content-Type", "application/json")
                    .header("x-client-id", clientId)
                    .header("x-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(rawJson))
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(res.body());
            
            if (!"00".equals(rootNode.get("code").asText())) {
                throw new RuntimeException("PayOS API Error: " + rootNode.get("desc").asText());
            }

            JsonNode dataNode = rootNode.get("data");

            return vn.payos.type.CheckoutResponseData.builder()
                    .bin(dataNode.has("bin") && !dataNode.get("bin").isNull() ? dataNode.get("bin").asText() : "N/A")
                    .accountNumber(dataNode.has("accountNumber") && !dataNode.get("accountNumber").isNull() ? dataNode.get("accountNumber").asText() : "N/A")
                    .accountName(dataNode.has("accountName") && !dataNode.get("accountName").isNull() ? dataNode.get("accountName").asText() : "N/A")
                    .amount(dataNode.has("amount") ? dataNode.get("amount").asInt() : amount.intValue())
                    .description(dataNode.has("description") && !dataNode.get("description").isNull() ? dataNode.get("description").asText() : description)
                    .orderCode(dataNode.has("orderCode") ? dataNode.get("orderCode").asLong() : orderCode)
                    .currency(dataNode.has("currency") ? dataNode.get("currency").asText() : "VND")
                    .paymentLinkId(dataNode.has("paymentLinkId") ? dataNode.get("paymentLinkId").asText() : null)
                    .status(dataNode.has("status") ? dataNode.get("status").asText() : "PENDING")
                    .checkoutUrl(dataNode.has("checkoutUrl") ? dataNode.get("checkoutUrl").asText() : "")
                    .qrCode(dataNode.has("qrCode") ? dataNode.get("qrCode").asText() : "")
                    .build();

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    @Transactional
    public void handleWebhook(Webhook webhookData) {
        try {
            // Xác thực WebhookData từ PayOS
            WebhookData data = payOS.verifyPaymentWebhookData(webhookData);
            // WebhookData data = webhookData.getData();

            if ("00".equals(data.getCode()) || "00".equals(webhookData.getCode())) {
                Long orderCode = data.getOrderCode();
                Optional<Payment> paymentOpt = paymentRepository.findByOrderCode(orderCode);

                if (paymentOpt.isPresent()) {
                    Payment payment = paymentOpt.get();

                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        payment.setStatus(PaymentStatus.PAID);
                        payment.setTransactionId(data.getReference());
                        paymentRepository.save(payment);

                        // Kích hoạt/Tạo mới subscription và nạp tokens
                        subscriptionService.createOrUpdateSubscription(payment.getUser().getEmail(), payment.getPlan().getId());
                        System.out.println("Thanh toán thành công đơn hàng: " + orderCode + ". Đã cộng token/subscription.");
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Webhook processing error: " + e.getMessage());
        }
    }
}
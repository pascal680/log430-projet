package com.canbankx.payment.controller;

import com.canbankx.payment.dto.AuditLogResponseDTO;
import com.canbankx.payment.dto.PaymentRequestDTO;
import com.canbankx.payment.dto.PaymentResponseDTO;
import com.canbankx.payment.model.BankTransaction;
import com.canbankx.payment.repository.AuditLogRepository;
import com.canbankx.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/paymentservice/transactions")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AuditLogRepository auditLogRepository;

    @PostMapping
    public ResponseEntity<PaymentResponseDTO> submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequestDTO dto) {
        BankTransaction tx = paymentService.submit(idempotencyKey, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PaymentResponseDTO(tx));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(new PaymentResponseDTO(paymentService.getById(id)));
    }

    @GetMapping("/account/{accountNumber}/recent")
    public ResponseEntity<List<PaymentResponseDTO>> getRecent(@PathVariable String accountNumber) {
        return ResponseEntity.ok(
                paymentService.getRecentByAccountNumber(accountNumber)
                        .stream().map(PaymentResponseDTO::new).toList());
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponseDTO>> list(
            @RequestParam(required = false) String accountNumber) {
        List<PaymentResponseDTO> result = (accountNumber != null
                ? paymentService.getByAccountNumber(accountNumber)
                : paymentService.getAll())
                .stream().map(PaymentResponseDTO::new).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditLogResponseDTO>> getAuditTrail(@PathVariable UUID id) {
        paymentService.getById(id);
        return ResponseEntity.ok(
                auditLogRepository.findByTransactionIdOrderByCreatedAtAsc(id)
                        .stream().map(AuditLogResponseDTO::new).toList());
    }
}

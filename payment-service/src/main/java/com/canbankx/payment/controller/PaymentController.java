package com.canbankx.payment.controller;

import com.canbankx.payment.dto.AuditLogResponseDTO;
import com.canbankx.payment.dto.InterbankPaymentRequestDTO;
import com.canbankx.payment.dto.PaymentRequestDTO;
import com.canbankx.payment.dto.PaymentResponseDTO;
import com.canbankx.payment.model.BankTransaction;
import com.canbankx.payment.repository.AuditLogRepository;
import com.canbankx.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/paymentservice/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "UC-05 — Submit payments and transfers with exactly-once guarantee")
public class PaymentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final PaymentService paymentService;
    private final AuditLogRepository auditLogRepository;

    @PostMapping("/interbank")
    @Operation(
            summary = "Submit interbank transfer via central bank",
            description = "For transfers to external banks through the central bank choreography service. "
                + "Requires X-Participant-Id (this bank ID)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Interbank transfer accepted by central bank", content = @Content),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
            @ApiResponse(responseCode = "4XX/5XX", description = "Error from central bank", content = @Content)
    })
    public ResponseEntity<Map<String, Object>> submitInterbank(
            @RequestHeader("X-Participant-Id") String participantId,
            @Valid @org.springframework.web.bind.annotation.RequestBody InterbankPaymentRequestDTO dto) {
        Map<String, Object> response = paymentService.submitInterbank(participantId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping
    @Operation(
        summary     = "Submit a transaction (DEBIT / CREDIT / TRANSFER)",
        description = "Processes a payment with exactly-once semantics. "
                    + "Provide a unique `Idempotency-Key` header (UUID v4). "
                    + "Retrying with the **same key** returns the original response without re-executing the transaction."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transaction processed (COMPLETED or FAILED after compensation)",
            content = @Content(schema = @Schema(implementation = PaymentResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key header or validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Source or target account not found", content = @Content),
        @ApiResponse(responseCode = "422", description = "Insufficient funds on source account", content = @Content)
    })
    public ResponseEntity<PaymentResponseDTO> submit(
            @Parameter(
                name        = "Idempotency-Key",
                description = "Client-generated UUID v4 — reuse the same key to safely retry",
                required    = true,
                example     = "550e8400-e29b-41d4-a716-446655440000",
                in          = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @org.springframework.web.bind.annotation.RequestBody PaymentRequestDTO dto) {
        BankTransaction tx = paymentService.submit(idempotencyKey, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PaymentResponseDTO(tx));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transaction found",
            content = @Content(schema = @Schema(implementation = PaymentResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Transaction not found", content = @Content)
    })
    public ResponseEntity<PaymentResponseDTO> getById(
            @Parameter(description = "Transaction UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(new PaymentResponseDTO(paymentService.getById(id)));
    }

    @GetMapping("/account/{accountNumber}/recent")
    @Operation(summary = "Get 10 most recent transactions for an account")
    @ApiResponse(responseCode = "200", description = "Recent transactions (up to 10)",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentResponseDTO.class))))
    public ResponseEntity<List<PaymentResponseDTO>> getRecent(
            @Parameter(description = "Account number", example = "2536624609") @PathVariable String accountNumber) {
        return ResponseEntity.ok(
                paymentService.getRecentByAccountNumber(accountNumber)
                        .stream().map(PaymentResponseDTO::new).toList());
    }

    @GetMapping
    @Operation(
        summary     = "List transactions (paginated)",
        description = "Pass `accountNumber` to filter by account. Supports `?page=0&size=50` (max size=200, default DESC by createdAt)."
    )
    @ApiResponse(responseCode = "200", description = "Paginated transaction list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentResponseDTO.class))))
    public ResponseEntity<List<PaymentResponseDTO>> list(
            @Parameter(description = "Filter by account number (optional)", example = "2536624609")
            @RequestParam(required = false) String accountNumber,
            @Parameter(description = "Page index (0-based)", example = "0")
            @RequestParam(defaultValue = "0")  int page,
            @Parameter(description = "Page size (max 200)", example = "50")
            @RequestParam(defaultValue = "50") int size) {

        int clampedSize = Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(page, clampedSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        List<PaymentResponseDTO> result = (accountNumber != null
                ? paymentService.getByAccountNumber(accountNumber, pageRequest)
                : paymentService.getAll(pageRequest))
                .stream().map(PaymentResponseDTO::new).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Get audit trail for a transaction", description = "Returns the full append-only audit log — every step from INITIATED to COMPLETED or FAILED")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit trail entries",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AuditLogResponseDTO.class)))),
        @ApiResponse(responseCode = "404", description = "Transaction not found", content = @Content)
    })
    public ResponseEntity<List<AuditLogResponseDTO>> getAuditTrail(
            @Parameter(description = "Transaction UUID") @PathVariable UUID id) {
        paymentService.getById(id);
        return ResponseEntity.ok(
                auditLogRepository.findByTransactionIdOrderByCreatedAtAsc(id)
                        .stream().map(AuditLogResponseDTO::new).toList());
    }
}
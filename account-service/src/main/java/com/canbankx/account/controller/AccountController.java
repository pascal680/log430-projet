package com.canbankx.account.controller;

import com.canbankx.account.dto.AccountCreationDTO;
import com.canbankx.account.dto.AccountResponseDTO;
import com.canbankx.account.dto.BalanceUpdateDTO;
import com.canbankx.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accountservice/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "UC-03 / UC-04 — Open accounts, query balances, debit/credit operations")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @Operation(summary = "Open a new bank account", description = "Creates a CHECKING or SAVINGS account for an existing ACTIVE client")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created",
            content = @Content(schema = @Schema(implementation = AccountResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Validation error or invalid account type", content = @Content),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<AccountResponseDTO> createAccount(@Valid @RequestBody AccountCreationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AccountResponseDTO(accountService.createAccount(dto)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by UUID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account found",
            content = @Content(schema = @Schema(implementation = AccountResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Account not found", content = @Content)
    })
    public ResponseEntity<AccountResponseDTO> getById(
            @Parameter(description = "Account UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(new AccountResponseDTO(accountService.getAccountById(id)));
    }

    @GetMapping("/number/{accountNumber}")
    @Operation(summary = "Get account by account number")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account found",
            content = @Content(schema = @Schema(implementation = AccountResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Account not found", content = @Content)
    })
    public ResponseEntity<AccountResponseDTO> getByNumber(
            @Parameter(description = "10-digit account number", example = "2536624609") @PathVariable String accountNumber) {
        return ResponseEntity.ok(new AccountResponseDTO(accountService.getAccountByNumber(accountNumber)));
    }

    @GetMapping
    @Operation(summary = "List accounts", description = "Pass `clientId` to filter by client, or omit for all accounts")
    @ApiResponse(responseCode = "200", description = "Account list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = AccountResponseDTO.class))))
    public ResponseEntity<List<AccountResponseDTO>> list(
            @Parameter(description = "Filter by client UUID (optional)") @RequestParam(required = false) UUID clientId) {
        List<AccountResponseDTO> result = (clientId != null
                ? accountService.getAccountsByClientId(clientId)
                : accountService.getAllAccounts())
                .stream().map(AccountResponseDTO::new).toList();
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/number/{accountNumber}/debit")
    @Operation(summary = "Debit an account", description = "Internal endpoint called by payment-service. Atomically subtracts amount only if balance is sufficient")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Debit applied — returns updated account",
            content = @Content(schema = @Schema(implementation = AccountResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Account not found", content = @Content),
        @ApiResponse(responseCode = "422", description = "Insufficient funds", content = @Content)
    })
    public ResponseEntity<AccountResponseDTO> debit(
            @Parameter(description = "Account number", example = "2536624609") @PathVariable String accountNumber,
            @Valid @RequestBody BalanceUpdateDTO dto) {
        accountService.debit(accountNumber, dto.getAmount());
        return ResponseEntity.ok(new AccountResponseDTO(accountService.getAccountByNumber(accountNumber)));
    }

    @PatchMapping("/number/{accountNumber}/credit")
    @Operation(summary = "Credit an account", description = "Internal endpoint called by payment-service. Atomically adds amount to the balance")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Credit applied — returns updated account",
            content = @Content(schema = @Schema(implementation = AccountResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Account not found", content = @Content)
    })
    public ResponseEntity<AccountResponseDTO> credit(
            @Parameter(description = "Account number", example = "2536624609") @PathVariable String accountNumber,
            @Valid @RequestBody BalanceUpdateDTO dto) {
        accountService.credit(accountNumber, dto.getAmount());
        return ResponseEntity.ok(new AccountResponseDTO(accountService.getAccountByNumber(accountNumber)));
    }
}
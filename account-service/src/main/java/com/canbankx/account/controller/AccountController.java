package com.canbankx.account.controller;

import com.canbankx.account.dto.AccountCreationDTO;
import com.canbankx.account.dto.AccountResponseDTO;
import com.canbankx.account.dto.BalanceUpdateDTO;
import com.canbankx.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Accounts", description = "Bank account management")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @Operation(summary = "Open a new account")
    public ResponseEntity<AccountResponseDTO> createAccount(@Valid @RequestBody AccountCreationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AccountResponseDTO(accountService.createAccount(dto)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by UUID")
    public ResponseEntity<AccountResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(new AccountResponseDTO(accountService.getAccountById(id)));
    }

    @GetMapping("/number/{accountNumber}")
    @Operation(summary = "Get account by account number")
    public ResponseEntity<AccountResponseDTO> getByNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(new AccountResponseDTO(accountService.getAccountByNumber(accountNumber)));
    }

    @GetMapping
    @Operation(summary = "List accounts", description = "Pass clientId to filter by client, or omit for all accounts")
    public ResponseEntity<List<AccountResponseDTO>> list(@RequestParam(required = false) UUID clientId) {
        List<AccountResponseDTO> result = (clientId != null
                ? accountService.getAccountsByClientId(clientId)
                : accountService.getAllAccounts())
                .stream().map(AccountResponseDTO::new).toList();
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/number/{accountNumber}/debit")
    @Operation(summary = "Debit an account")
    public ResponseEntity<AccountResponseDTO> debit(
            @PathVariable String accountNumber, @Valid @RequestBody BalanceUpdateDTO dto) {
        return ResponseEntity.ok(
                new AccountResponseDTO(accountService.debit(accountNumber, dto.getAmount())));
    }

    @PatchMapping("/number/{accountNumber}/credit")
    @Operation(summary = "Credit an account")
    public ResponseEntity<AccountResponseDTO> credit(
            @PathVariable String accountNumber, @Valid @RequestBody BalanceUpdateDTO dto) {
        return ResponseEntity.ok(
                new AccountResponseDTO(accountService.credit(accountNumber, dto.getAmount())));
    }
}
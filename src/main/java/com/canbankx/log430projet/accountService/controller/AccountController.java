package com.canbankx.log430projet.accountService.controller;

import com.canbankx.log430projet.accountService.dto.AccountCreationDTO;
import com.canbankx.log430projet.accountService.model.Account;
import com.canbankx.log430projet.accountService.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accountservice/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * POST /accountservice/accounts
     * Crée un nouveau compte bancaire.
     *
     * @param dto { clientId, accountType, initialDeposit }
     * @return 201 Created + le compte créé
     */
    @PostMapping
    public ResponseEntity<Account> createAccount(@Valid @RequestBody AccountCreationDTO dto) {
        Account created = accountService.createAccount(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
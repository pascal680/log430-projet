package com.canbankx.identity.controller;

import com.canbankx.identity.dto.ClientRegistrationDTO;
import com.canbankx.identity.dto.ClientResponseDTO;
import com.canbankx.identity.dto.OtpVerifyDTO;
import com.canbankx.identity.model.enums.Status;
import com.canbankx.identity.service.ClientService;
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
@RequestMapping("/identityservice/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Client registration and management")
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    @Operation(summary = "Register a new client", description = "Creates a PENDING account and sends an OTP to the client's email")
    public ResponseEntity<ClientResponseDTO> register(@Valid @RequestBody ClientRegistrationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ClientResponseDTO(clientService.register(dto)));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify email OTP", description = "Activates the client account by validating the 6-digit OTP sent by email")
    public ResponseEntity<ClientResponseDTO> verifyOtp(@PathVariable UUID id, @Valid @RequestBody OtpVerifyDTO dto) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.verifyOtp(id, dto.getOtpCode())));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Admin activate", description = "Directly activates a client (admin use, bypasses OTP)")
    public ResponseEntity<ClientResponseDTO> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.activate(id)));
    }

    @GetMapping
    @Operation(summary = "List all clients")
    public ResponseEntity<List<ClientResponseDTO>> listAll() {
        return ResponseEntity.ok(clientService.getAll().stream()
                .map(ClientResponseDTO::new).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID")
    public ResponseEntity<ClientResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.getById(id)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update client status")
    public ResponseEntity<ClientResponseDTO> updateStatus(
            @PathVariable UUID id, @RequestParam Status status) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.updateStatus(id, status)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate client")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        clientService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
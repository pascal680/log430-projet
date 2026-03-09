package com.canbankx.identity.controller;

import com.canbankx.identity.dto.ClientRegistrationDTO;
import com.canbankx.identity.dto.ClientResponseDTO;
import com.canbankx.identity.dto.OtpVerifyDTO;
import com.canbankx.identity.model.enums.Status;
import com.canbankx.identity.service.ClientService;
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
@RequestMapping("/identityservice/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "UC-01 — Client registration, KYC verification and lifecycle management")
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    @Operation(summary = "Register a new client", description = "Creates a PENDING account and sends a 6-digit OTP to the client's email for KYC verification")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Client registered — status PENDING, OTP sent by email",
            content = @Content(schema = @Schema(implementation = ClientResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Validation error (missing field, bad email format)", content = @Content),
        @ApiResponse(responseCode = "409", description = "Email or NAS already registered", content = @Content)
    })
    public ResponseEntity<ClientResponseDTO> register(@Valid @RequestBody ClientRegistrationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ClientResponseDTO(clientService.register(dto)));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify email OTP (KYC)", description = "Validates the 6-digit OTP sent during registration. On success the client transitions to ACTIVE")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OTP valid — client is now ACTIVE",
            content = @Content(schema = @Schema(implementation = ClientResponseDTO.class))),
        @ApiResponse(responseCode = "401", description = "OTP invalid or expired", content = @Content),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<ClientResponseDTO> verifyOtp(
            @Parameter(description = "Client UUID") @PathVariable UUID id,
            @Valid @RequestBody OtpVerifyDTO dto) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.verifyOtp(id, dto.getOtpCode())));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Admin activate (bypass OTP)", description = "Directly activates a client without OTP — for admin use and automated tests")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Client activated",
            content = @Content(schema = @Schema(implementation = ClientResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<ClientResponseDTO> activate(
            @Parameter(description = "Client UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.activate(id)));
    }

    @GetMapping
    @Operation(summary = "List all clients")
    @ApiResponse(responseCode = "200", description = "List of all clients",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ClientResponseDTO.class))))
    public ResponseEntity<List<ClientResponseDTO>> listAll() {
        return ResponseEntity.ok(clientService.getAll().stream()
                .map(ClientResponseDTO::new).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Client found",
            content = @Content(schema = @Schema(implementation = ClientResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<ClientResponseDTO> getById(
            @Parameter(description = "Client UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.getById(id)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update client status (ACTIVE / SUSPENDED)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated",
            content = @Content(schema = @Schema(implementation = ClientResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<ClientResponseDTO> updateStatus(
            @Parameter(description = "Client UUID") @PathVariable UUID id,
            @Parameter(description = "New status", example = "SUSPENDED") @RequestParam Status status) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.updateStatus(id, status)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate (delete) a client")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Client deleted", content = @Content),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<Void> deactivate(
            @Parameter(description = "Client UUID") @PathVariable UUID id) {
        clientService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
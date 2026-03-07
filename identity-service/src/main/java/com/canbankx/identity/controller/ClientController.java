package com.canbankx.identity.controller;

import com.canbankx.identity.dto.ClientRegistrationDTO;
import com.canbankx.identity.dto.ClientResponseDTO;
import com.canbankx.identity.model.enums.Status;
import com.canbankx.identity.service.ClientService;
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
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    public ResponseEntity<ClientResponseDTO> register(@Valid @RequestBody ClientRegistrationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ClientResponseDTO(clientService.register(dto)));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ClientResponseDTO> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.activate(id)));
    }

    @GetMapping
    public ResponseEntity<List<ClientResponseDTO>> listAll() {
        return ResponseEntity.ok(clientService.getAll().stream()
                .map(ClientResponseDTO::new).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.getById(id)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ClientResponseDTO> updateStatus(
            @PathVariable UUID id, @RequestParam Status status) {
        return ResponseEntity.ok(new ClientResponseDTO(clientService.updateStatus(id, status)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        clientService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}

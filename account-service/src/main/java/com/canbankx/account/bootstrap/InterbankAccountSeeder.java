package com.canbankx.account.bootstrap;

import com.canbankx.account.model.Account;
import com.canbankx.account.model.enums.AccountType;
import com.canbankx.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterbankAccountSeeder {

    private final AccountRepository accountRepository;

    @Value("${bank.participant-id:participant-003}")
    private String participantId;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedInterbankAccounts() {
        List<SeedAccount> seeds = switch (participantId) {
            case "participant-001" -> List.of(
                    new SeedAccount("1000100001", "2f9e8c0d-4f44-4e2d-bfd2-6e34c42cb75a", AccountType.CHECKING, new BigDecimal("10000.00")),
                    new SeedAccount("1000200001", "0f8ab79c-c0e3-4359-a678-f5f3f7f8a69d", AccountType.CHECKING, new BigDecimal("10000.00")),
                    new SeedAccount("1000700002", "f42f9a45-78be-4fa0-8ce5-efc9120d4a03", AccountType.SAVINGS,  new BigDecimal("10000.00"))
            );
            case "participant-002" -> List.of(
                    new SeedAccount("2000300001", "30741c6b-68bb-40c0-a9a1-0df95b5f3d67", AccountType.CHECKING, new BigDecimal("10000.00")),
                    new SeedAccount("2000400001", "53fb85d8-fd87-45f8-bfa4-e0aa2a939516", AccountType.CHECKING, new BigDecimal("10000.00")),
                    new SeedAccount("2000500001", "592af0e7-e89c-464f-95a6-7ce60de0f8d9", AccountType.CHECKING, new BigDecimal("10000.00"))
            );
            default -> List.of(
                    new SeedAccount("3000600001", "4f1b88ec-3961-49d9-a0aa-db5a27f97f72", AccountType.CHECKING, new BigDecimal("10000.00")),
                    new SeedAccount("3000700001", "5a88b862-3c62-4f9b-b5f4-3741a762f691", AccountType.CHECKING, new BigDecimal("10000.00")),
                    new SeedAccount("3000800001", "fd5f8c60-920f-4b84-a64e-4f13f6acf9ef", AccountType.CHECKING, new BigDecimal("10000.00"))
            );
        };

        int created = 0;
        for (SeedAccount seed : seeds) {
            if (accountRepository.existsByAccountNumber(seed.accountNumber)) {
                continue;
            }
            Account account = Account.builder()
                    .accountNumber(seed.accountNumber)
                    .clientId(UUID.fromString(seed.clientId))
                    .type(seed.type)
                    .balance(seed.balance)
                    .build();
            accountRepository.save(account);
            created++;
        }

        log.info("Interbank account seed completed for {} (created={})", participantId, created);
    }

    private record SeedAccount(String accountNumber, String clientId, AccountType type, BigDecimal balance) {}
}

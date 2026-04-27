package com.evolutionnext.structuredconcurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("preview")
public class AccountingServiceTest {

    UserService userService;
    InvoiceService invoiceService;
    AccountingService accountingService;

    @BeforeEach
    public void beforeEach() {
        userService = new UserService();
        invoiceService = new InvoiceService();
        accountingService = new AccountingService(userService, invoiceService);
    }

    @Test
    void testStructuredConcurrency() throws InterruptedException {
        UserInvoices userInvoice = accountingService.findAllInvoicesByUser(1L);
        assertThat(userInvoice.invoices()).hasSize(3);
        assertThat(userInvoice.user().firstName()).isEqualTo("Simon");
        assertThat(userInvoice.user().lastName()).isEqualTo("Roberts");
    }

    @Test
    void testStructuredConcurrencyWithSubtasks() throws InterruptedException {
        Optional<UserInvoices> userInvoiceOptional =
            accountingService.findAllInvoicesByUserUsingSubtask(1L);
        assertThat(userInvoiceOptional).isNotEmpty();
        UserInvoices userInvoices = userInvoiceOptional.get();
        assertThat(userInvoices.invoices()).hasSize(3);
        User user = userInvoices.user();
        assertThat(user.firstName()).isEqualTo("Simon");
        assertThat(user.lastName()).isEqualTo("Roberts");
    }

    @Test
    void testStructuredConcurrencyWithError() {
        assertThatThrownBy(() ->
            accountingService.findAllInvoicesByUserWithFailedUserService(90L))
            .isInstanceOf(StructuredTaskScope.FailedException.class);
    }

    @Test
    void testStructuredConcurrencyWithErrorAndLongerInvoiceService() {
        assertThatThrownBy(() ->
            accountingService.findAllInvoicesByUserWithLatencyService(90L))
            .isInstanceOf(StructuredTaskScope.FailedException.class);
    }

    @Test
    void testStructuredConcurrencyWithOnSuccess()
        throws ExecutionException, InterruptedException {
        String result = accountingService.findAllEitherUserOrInvoices(1L);
        assertThat(result).isEqualTo("User: Simon Roberts");
    }

    @Test
    void testStructuredConcurrencyWithOnSuccessWithUserServiceLatency()
        throws ExecutionException, InterruptedException {
        String result = accountingService.findAllEitherUserOrInvoicesFromUserServiceWithLatency(90L);
        String expected = "A list of [Invoice[number=402, amount=1120.0], Invoice[number=1402, amount=1220.0], Invoice[number=671, amount=1220.0]]";
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void testStructuredConcurrencyWithAllSuccessfulOrThrow() throws ExecutionException, InterruptedException {
        List<User> allUsers = accountingService.findAllUsers(1L, 2L, 3L);
        assertThat(allUsers).hasSize(3);
    }

    @Test
    void testStructuredConcurrencyWithAwaitAll() throws InterruptedException {
        accountingService.reportAllUsers(1L, 2L, 3L);
    }

    @Test
    void testStructuredConcurrencyWithPreemptionUsingAwaitUntil() {
        assertThatThrownBy(() ->
            accountingService.findAllUserAndInvoicesWithPreemption(1L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testStructuredConcurrencyWithATimeout() throws InterruptedException {
        assertThatThrownBy(() -> accountingService.findAllInvoicesWithTimeout(1L))
            .isInstanceOf(StructuredTaskScope.TimeoutException.class);
    }


}

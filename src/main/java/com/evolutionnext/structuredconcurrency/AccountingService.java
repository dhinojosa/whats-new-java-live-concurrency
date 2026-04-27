package com.evolutionnext.structuredconcurrency;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Supplier;

@SuppressWarnings("preview")
public class AccountingService {
    private final UserService userService;
    private final InvoiceService invoiceService;

    public AccountingService(UserService userService, InvoiceService invoiceService) {
        this.userService = userService;
        this.invoiceService = invoiceService;
    }

    public UserInvoices findAllInvoicesByUser(Long id)
        throws InterruptedException {
        try (var scope = StructuredTaskScope.open()) {
            StructuredTaskScope.Subtask<User> user =
                scope.fork(() -> userService.findUser(id));
            StructuredTaskScope.Subtask<List<Invoice>> order =
                scope.fork(() -> invoiceService.findAllInvoicesByUser(id));

            scope.join();

            // Here, both subtasks have succeeded, so compose their results
            return new UserInvoices(user.get(), order.get());
        }
    }



    /**
     * While you can return a `Supplier<T>` from fork, you may choose to bring in `Subtask`. Subtask
     * is a `Supplier` but it has extra APIs, like `state()` so you can do refined querying of the status
     * of your subtask. Notice that there is no `ExecutionException` anymore, since we are not throwing an exception
     * from our scope.
     *
     * @param id ID of the User
     * @return UserInvoices
     * @throws InterruptedException if the tasks are interrupted
     */
    public Optional<UserInvoices> findAllInvoicesByUserUsingSubtask(Long id) throws InterruptedException {
        try (var scope = StructuredTaskScope.open()) {

            StructuredTaskScope.Subtask<User> user = scope.fork(() -> userService.findUser(id));
            StructuredTaskScope.Subtask<List<Invoice>> order = scope.fork(() -> invoiceService.findAllInvoicesByUser(id));

            scope.join();

            StructuredTaskScope.Subtask.State userState = user.state();
            StructuredTaskScope.Subtask.State orderState = order.state();

            if (userState.equals(StructuredTaskScope.Subtask.State.FAILED) ||
                orderState.equals(StructuredTaskScope.Subtask.State.FAILED)) {
                return Optional.empty();
            } else {
                // Here, both subtasks have succeeded, so compose their results
                return Optional.of(new UserInvoices(user.get(), order.get()));
            }
        }
    }

    public UserInvoices findAllInvoicesByUserWithFailedUserService(long id) throws InterruptedException {
        try (var scope = StructuredTaskScope
            .open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            Supplier<User> user = scope.fork(() -> userService.findUser(id));
            Supplier<List<Invoice>> order = scope.fork(() ->
                invoiceService.findAllInvoicesByUser(id));
            scope.join();
            // Here, both subtasks have succeeded, so compose their results
            return new UserInvoices(user.get(), order.get());
        }
    }

    public UserInvoices findAllInvoicesByUserWithLatencyService(long id) throws InterruptedException {
        try (var scope = StructuredTaskScope.open(
                 StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {

            Supplier<User> user = scope.fork(() -> userService.findUser(id));
            Supplier<List<Invoice>> order = scope.fork(() ->
                invoiceService.findAllInvoicesByUserLongTime(id));
            scope.join();
            // Here, both subtasks have succeeded, so compose their results
            return new UserInvoices(user.get(), order.get());
        }
    }

    public String findAllEitherUserOrInvoices(long id) throws InterruptedException {
        try (var scope = StructuredTaskScope.open(
            StructuredTaskScope.Joiner.anySuccessfulOrThrow())) {

            scope.fork(() -> userService.findUser(id));
            scope.fork(() -> invoiceService.findAllInvoicesByUserLongTime(id));

            return switch (scope.join()) {
                case User(var firstName, var lastName) -> String.format("User: %s %s", firstName, lastName);
                case List<?> list -> String.format("A list of %s", list);
                default -> "Unknown";
            };
        }
    }

    public String findAllEitherUserOrInvoicesFromUserServiceWithLatency(long id) throws InterruptedException {
        try (var scope = StructuredTaskScope.open(
            StructuredTaskScope.Joiner.anySuccessfulOrThrow())) {
            scope.fork(() -> userService.findUserLongTime(id));
            scope.fork(() -> invoiceService.findAllInvoicesByUser(id));
            return switch (scope.join()) {
                case User(var firstName, var lastName) ->
                    String.format("User: %s %s", firstName, lastName);
                case List<?> list -> String.format("A list of %s", list);
                default -> "Unknown";
            };
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public List<User> findAllUsers(long... ids) throws InterruptedException {
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<User>allSuccessfulOrThrow())) {
            Arrays.stream(ids).boxed().forEach(id -> scope.fork(() -> userService.findUser(id)));
            // Here I expect all subtasks to be a User, so I can call scope.join() with a result
            return scope.join();
        }
    }

    public void reportAllUsers(long... ids) throws InterruptedException {
        // Await all is for side effects, notice the result type of scope.join() is void
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<User>awaitAll())) {
            Arrays.stream(ids).boxed().forEach(i -> scope.fork(() ->
                System.out.printf("User retrieved and side-effected %s%n", userService.findUser(i))));
            scope.join();
        }
    }

    @SuppressWarnings({"DuplicatedCode", "UnusedReturnValue"})
    public UserInvoices findAllUserAndInvoicesWithPreemption(long id) throws InterruptedException {
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.allUntil(subtask ->
            subtask.state().equals(StructuredTaskScope.Subtask.State.SUCCESS)))) {
            StructuredTaskScope.Subtask<User> user = scope.fork(() -> userService.findUser(id));
            StructuredTaskScope.Subtask<List<Invoice>> invoices = scope.fork(() -> invoiceService.findAllInvoicesByUserLongTime(id));
            scope.join();
            return new UserInvoices(user.get(), invoices.get());
        }
    }

    @SuppressWarnings({"DuplicatedCode", "UnusedReturnValue"})
    public UserInvoices findAllInvoicesWithTimeout(Long id) throws InterruptedException {
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.allSuccessfulOrThrow(),
            config -> config.withTimeout(Duration.of(500, ChronoUnit.MILLIS)))) {
            StructuredTaskScope.Subtask<User> user = scope.fork(() -> userService.findUser(id));
            StructuredTaskScope.Subtask<List<Invoice>> invoices = scope.fork(() -> invoiceService.findAllInvoicesByUserLongTime(id));
            scope.join();
            return new UserInvoices(user.get(), invoices.get());
        }
    }
}

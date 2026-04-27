package com.evolutionnext.structuredconcurrency;

import java.util.List;

public record UserInvoices(User user, List<Invoice> invoices) {
}

CREATE TABLE bill_cycles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    day_of_month SMALLINT NOT NULL,
    next_run_date DATE NOT NULL
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    bill_cycle_id UUID NOT NULL REFERENCES bill_cycles(id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    sub_total DECIMAL(10,2) NOT NULL,
    tax DECIMAL(10,2) NOT NULL,
    grand_total DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    due_date DATE NOT NULL,
    issued_at TIMESTAMP,
    pdf_ref VARCHAR(500)
);

CREATE TABLE invoice_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    description VARCHAR(255) NOT NULL,
    quantity DECIMAL(10,4) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) NOT NULL
);

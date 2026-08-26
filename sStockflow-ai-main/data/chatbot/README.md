# StockFlow demo planning data

The original CSV files provide locations, products, batches, available units,
reorder levels, expiry dates, and update timestamps. They do not provide sales
history, unit costs, supplier lead times, route options, vehicle capacities,
emission factors, or approval records.

The additional files in this folder are explicitly labeled demo/synthetic:

- `planning_assumptions.csv`: demand rate, lead time, safety-stock policy, and valuation fallback.
- `routes.csv`: Chennai-Bengaluru route alternatives used by the acceptance questions.
- `vehicles.csv`: demo capacity, cost, emissions, and availability.
- `approved_transfers.csv`: one demo approved transfer used to demonstrate sustainability reporting.

Replace these rows with real company data before using the outputs for purchasing,
dispatch, financial reporting, or approval decisions. The chatbot includes the
demo warning in its evidence/warnings so synthetic values are not presented as
production facts.

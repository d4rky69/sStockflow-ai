# Demo Script: District Map and Status Panels

## Pre-requisites
1. Ensure the backend and frontend are running (`RUN_ALL_WINDOWS.cmd`).
2. Login with the standard demo tenant credentials.

## Step 1: Navigating to the District Map
- From the Dashboard, click on **District Map** under the INTELLIGENCE section in the left navigation menu.
- Note the map view rendering districts with actual boundaries.

## Step 2: Exploring the Data
- Hover over the districts colored in Blue (`FORECAST_AVAILABLE`).
- Click on a district to see the **Popup** which includes:
  - District Name and ID.
  - Forecasting Status (Forecast Available or No Data).
  - Provenance details: Data source, Extraction Time, and Validity.
- Observe the Grey districts (`NO_DATA`), representing regions where baseline sales or forecasting is not currently available.

## Step 3: Assistant Integration
- Open the StockFlow AI Copilot by clicking "Ask StockFlow AI".
- Ask a question like: *"What is the forecasting status of the North District?"*
- Observe that the assistant leverages the canonical registry to answer strictly based on current API evidence.

## Step 4: Verification of Tenant Isolation
- Change the Tenant using the Top-Right Tenant Picker.
- Observe how the District Map reloads or clears out, proving that the endpoints restrict the data payload based on the `X-Tenant-ID` header.

# StockFlow Offline Sync Center (Mobile)

A premium, offline-first React application designed for field agents and delivery drivers. This module provides tactical navigation, resilient offline sync capabilities, and real-time incident reporting with a high-end dark mode aesthetic.

## Features

- **Driver Dashboard**: A clean, intuitive HUD summarizing route status, upcoming stops, and vehicle telemetry.
- **Resilient Navigation**: Tactical map interface with turn-by-turn routing, capable of falling back to cached offline map packages when connectivity drops.
- **Offline Sync Center**: A centralized queue that handles offline data storage. Automatically synchronizes field reports, incidents, and telemetry back to the server once a network connection is restored.
- **Incident Reporting**: Real-time hazard and SOS reporting with photo compression and geolocation tagging.

## Tech Stack

- React 18
- Vite
- Tailwind CSS (Premium Dark Theme)
- Framer Motion (Micro-animations and layout transitions)
- Supabase (Backend/Realtime Sync)

## Quick Start

### Prerequisites
- Node.js (v18 or higher recommended)
- npm

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/d4rky69/sStockflow-ai.git
   cd apps/offline-sync-center-mobile
   ```

2. **Install dependencies**
   ```bash
   npm install
   ```

3. **Configure Environment**
   Rename `.env.example` to `.env.local` and add your required credentials.

4. **Run the Development Server**
   ```bash
   npm run dev
   ```
   The application will start at `http://localhost:3000`.

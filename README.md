# DiskTerminal-Unofficial

A 1.7.10 (GTNH) port of the **DiskTerminal** mod — a terminal and wireless terminal for managing every storage cell and storage bus across an ME network.

> Unofficial port. Hard dependency: **Applied Energistics 2 Unofficial**.

[中文说明 / Chinese README](README_zh.md)

## Features

- **Terminal & wireless access** — install the Cell Terminal part on an ME cable, or access remotely with the Wireless Cell Terminal. As usual, link the wireless terminal to your network through the Security Terminal.
- **Multi-cell support** — works with all AE2 storage cell types (items, fluids, and essentia via Thaumic Energistics).
- **Network overview** — lists every ME Drive, ME Chest, and storage bus on the network, grouped by location.
- **Cell management** — view cell capacity (used / total bytes and storage type), eject cells straight to your inventory, preview stored contents and amounts, and edit partitions by drag-and-drop.
- **View modes** — Terminal (compact list), Cell Inventory (grid), Cell Partition (grid), Storage Bus Inventory, Storage Bus Partition.
- **Search & filter** — find items across all cells / storage buses using inventory / partition / mixed search modes, with an advanced query syntax.
- **World highlight** — double-click any storage entry to highlight the block in the world and print its coordinates in chat.
- **Priority management** — set ME Chest, ME Drive, and storage bus priorities directly in the GUI.
- **Quick-partition keybinds** — configurable keys to partition the hovered item into a matching cell.

## Integrations

- **Applied Energistics 2 Unofficial** (required)
- **AE2 Fluid Craft – Rework** — fluid cells and fluid storage buses
- **Thaumic Energistics** — essentia cells and storage buses
- **NotEnoughItems** — drag ingredients into partitions, quick-partition from the item list
- **GregTech 5 Unofficial** — scans GT ME buses / hatches

## Building

```bash
./gradlew build
```

## License

See the upstream DiskTerminal project for licensing. This is an unofficial community port.

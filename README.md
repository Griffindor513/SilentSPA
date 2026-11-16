# SilentSPA

**SilentSPA** is a lightweight, client-side Fabric mod for Minecraft that implements **SPA (Single Packet Authorization)**. It allows players to seamlessly connect to servers protected by `fwknopd` firewalls without any manual steps.

## 🛡️ Why use SilentSPA?
If you run a public Minecraft server, exposing port `25565` leaves you vulnerable to port scanners and DDoS attacks. Configuring your server to `DROP` all incoming traffic by default is the ultimate defense, but it prevents legitimate players from joining. **SilentSPA solves this** by sending an encrypted UDP "knock" to the server, temporarily opening the firewall only for the player's IP.

## ✨ Features
*   **Standard fwknopd Protocol**: Generates fully standard, AES-encrypted, and HMAC-signed SPA packets.
*   **Zero-Lag Asynchronous I/O**: Key reading, encryption, and UDP sending happen on background threads. It will *never* freeze your game.
*   **Smart Voice Chat Support**: Auto-detects Simple Voice Chat / Plasmo Voice and includes their UDP ports in the knock.
*   **In-Game Key Manager**: Create, edit, or select `.fwknoprc` files directly inside Minecraft.
*   **Secure NBT Persistence**: Only the absolute file path is saved to `servers.dat`.

## 🛠️ Quick Start
1. Download the mod and place it in your `mods` folder.
2. Add the protected server to your Multiplayer list.
3. Click **"SPA Key: Unbound"** to select your `.fwknoprc` file (or generate a new one in-game).
4. Click **Join Server**. SilentSPA will knock, wait briefly, and connect seamlessly!

## 📜 License
This project is licensed under the **Minecraft Mod Public License (MMPL) v1.0.1**. 
*Note: The MMPL applies to the original code of SilentSPA. Dependencies like Fabric API remain under their respective licenses.*

## 🤖 AI Disclosure
The core codebase was developed with the assistance of generative AI (DeepSeek), heavily guided, architected, and debugged by a human developer. 
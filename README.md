# ☠ ZombieInfection – Paper 26.3 (Java 25)

## Bauen
```
gradle build          # oder in IntelliJ öffnen -> Gradle -> build
```
Die fertige Datei liegt dann in `build/libs/ZombieInfection-1.0.0.jar` -> in `plugins/` des Paper-26.3-Servers kopieren.
(Abhängigkeit: `io.papermc.paper:paper-api:26.3.build.+`, JDK 25.)

## Zombie-Skin einrichten (wichtig!)
Minecraft zeigt fremde Skins nur, wenn sie von Mojang signiert sind. Deshalb:
1. Beim ersten Serverstart liegt `plugins/ZombieInfection/zombie_skin.png` (dein Skin).
2. Auf https://mineskin.org hochladen und generieren.
3. **Texture value** und **Signature** in `plugins/ZombieInfection/config.yml` unter `skin:` eintragen.
4. `/zombie reload`

## Befehle
- `/start` oder `/zombie start` – Runde starten (Border um den Startenden)
- `/zombie` – Menü (Timer, Border, Zombie-Anzahl, 7 Features, Start/Stop)
- `/zombie stop | reload | status`
- Permission: `zombie.admin` (Standard: OP)

## Ablauf
1. Border (Radius 50) -> roter Countdown 5..1 -> Border fällt
2. Roter Boss-Bar-Timer (Standard 2:00) – danach Schwarzbild + Blindheit, Namen & Tab-Einträge weg
3. Zombies wechseln den Skin, bekommen Stärke I + Speed I (permanent); Enthüllung: INFIZIERT / NICHT INFIZIERT
4. Schutzphase (Standard 5 Min): Zombies können nicht angreifen. Nur Zombies sehen den Witterungs-Kompass.
5. Jagd: tödlicher Zombie-Treffer = epische Verwandlung statt Tod
6. Überlebender tötet Zombie = +3 Herzen permanent (Animation), Zombie wartet 10 s als Spectator
7. Alle infiziert -> roter Nebel, "DIESER SERVER WURDE VON EUCH ÜBERNOMMEN"
   Zeit läuft ab -> Überlebende werden zusammengeholt, Feuerwerk, "IHR HABT MINECRAFTS SCHLIMMSTE APOKALYPSE ÜBERLEBT"

## Die 7 Zusatz-Features (im Menü schaltbar)
Herzschlag · Blutnacht · Nebel des Grauens · Blutrausch · Zombie-Gebrüll · Letzter Überlebender · Nachschub-Kisten

## Hinweis
Dieser Code wurde ohne Zugriff auf die Paper-API geschrieben und ist nicht kompiliert oder getestet.
Falls beim Bauen ein Methodenname nicht passt (z. B. `unlistPlayer`/`listPlayer`), meldet Gradle die genaue Zeile.

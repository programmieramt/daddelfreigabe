#!/bin/bash
# Sperrt Dienste fuer einen AdGuard-Client.
# Credentials per Environment oder .env-Datei im selben Verzeichnis.
#
# Crontab-Eintrag (taeglich 3:00 Uhr):
#   0 3 * * * /path/to/lock-youtube.sh
#
# .env Beispiel:
#   SERVICES=youtube,tiktok,twitch,twitter

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
[ -f "$SCRIPT_DIR/.env" ] && . "$SCRIPT_DIR/.env"

: "${ADGUARD_URL:?Setze ADGUARD_URL}"
: "${ADGUARD_USER:?Setze ADGUARD_USER}"
: "${ADGUARD_PASS:?Setze ADGUARD_PASS}"
: "${CLIENT_IP:=192.168.68.53}"
: "${SERVICES:=youtube}"

CLIENT_JSON=$(curl -s -u "$ADGUARD_USER:$ADGUARD_PASS" "$ADGUARD_URL/control/clients")
if [ $? -ne 0 ]; then
    echo "$(date): Fehler beim Abrufen der Clients" >&2
    exit 1
fi

CLIENT_NAME=$(echo "$CLIENT_JSON" | jq -r --arg ip "$CLIENT_IP" '.clients[] | select(.ids[] == $ip) | .name')
CLIENT_DATA=$(echo "$CLIENT_JSON" | jq --arg ip "$CLIENT_IP" '.clients[] | select(.ids[] == $ip)')

if [ -z "$CLIENT_NAME" ]; then
    echo "$(date): Client $CLIENT_IP nicht gefunden" >&2
    exit 1
fi

SERVICES_JSON=$(echo "$SERVICES" | jq -R 'split(",")')
UPDATED_DATA=$(echo "$CLIENT_DATA" | jq --argjson svcs "$SERVICES_JSON" '.blocked_services = (.blocked_services + $svcs | unique)')

PAYLOAD=$(jq -n --arg name "$CLIENT_NAME" --argjson data "$UPDATED_DATA" '{"name": $name, "data": $data}')

RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -u "$ADGUARD_USER:$ADGUARD_PASS" \
    -H "Content-Type: application/json" \
    -X POST "$ADGUARD_URL/control/clients/update" \
    -d "$PAYLOAD")

if [ "$RESPONSE" = "200" ]; then
    echo "$(date): $SERVICES gesperrt fuer $CLIENT_NAME"
else
    echo "$(date): Fehler beim Sperren - HTTP $RESPONSE" >&2
    exit 1
fi

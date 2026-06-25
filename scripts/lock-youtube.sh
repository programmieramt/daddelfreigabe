#!/bin/bash
# Sperrt YouTube (und ggf. weitere Dienste) fuer einen AdGuard-Client.
# Credentials per Environment oder .env-Datei im selben Verzeichnis.
#
# Crontab-Eintrag (taeglich 3:00 Uhr):
#   0 3 * * * /path/to/lock-youtube.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
[ -f "$SCRIPT_DIR/.env" ] && . "$SCRIPT_DIR/.env"

: "${ADGUARD_URL:?Setze ADGUARD_URL}"
: "${ADGUARD_USER:?Setze ADGUARD_USER}"
: "${ADGUARD_PASS:?Setze ADGUARD_PASS}"
: "${CLIENT_IP:=192.168.68.53}"
: "${SERVICE:=youtube}"

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

ALREADY_BLOCKED=$(echo "$CLIENT_DATA" | jq -r --arg svc "$SERVICE" '.blocked_services | index($svc)')
if [ "$ALREADY_BLOCKED" != "null" ]; then
    echo "$(date): $SERVICE ist bereits gesperrt fuer $CLIENT_NAME"
    exit 0
fi

UPDATED_DATA=$(echo "$CLIENT_DATA" | jq --arg svc "$SERVICE" '.blocked_services += [$svc]')
PAYLOAD=$(jq -n --arg name "$CLIENT_NAME" --argjson data "$UPDATED_DATA" '{"name": $name, "data": $data}')

RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -u "$ADGUARD_USER:$ADGUARD_PASS" \
    -H "Content-Type: application/json" \
    -X POST "$ADGUARD_URL/control/clients/update" \
    -d "$PAYLOAD")

if [ "$RESPONSE" = "200" ]; then
    echo "$(date): $SERVICE gesperrt fuer $CLIENT_NAME"
else
    echo "$(date): Fehler beim Sperren - HTTP $RESPONSE" >&2
    exit 1
fi

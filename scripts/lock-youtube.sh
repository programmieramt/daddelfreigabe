#!/bin/bash
# Sperrt Dienste fuer AdGuard-Clients.
# Credentials per Environment oder .env-Datei im selben Verzeichnis.
#
# Crontab-Eintrag (taeglich 3:00 Uhr):
#   0 3 * * * /path/to/lock-youtube.sh
#
# .env Beispiel:
#   SERVICES=youtube,reddit
#   CLIENT_IPS=192.168.68.53

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
[ -f "$SCRIPT_DIR/.env" ] && . "$SCRIPT_DIR/.env"

: "${ADGUARD_URL:?Setze ADGUARD_URL}"
: "${ADGUARD_USER:?Setze ADGUARD_USER}"
: "${ADGUARD_PASS:?Setze ADGUARD_PASS}"
: "${CLIENT_IPS:=${CLIENT_IP:=192.168.68.53}}"
: "${SERVICES:=youtube,reddit}"

CLIENT_JSON=$(curl -s -u "$ADGUARD_USER:$ADGUARD_PASS" "$ADGUARD_URL/control/clients")
if [ $? -ne 0 ]; then
    echo "$(date): Fehler beim Abrufen der Clients" >&2
    exit 1
fi

SERVICES_JSON=$(echo "$SERVICES" | jq -R 'split(",")')
ERRORS=0

IFS=',' read -ra IPS <<< "$CLIENT_IPS"
for IP in "${IPS[@]}"; do
    CLIENT_NAME=$(echo "$CLIENT_JSON" | jq -r --arg ip "$IP" '.clients[] | select(.ids[] == $ip) | .name')
    CLIENT_DATA=$(echo "$CLIENT_JSON" | jq --arg ip "$IP" '.clients[] | select(.ids[] == $ip)')

    if [ -z "$CLIENT_NAME" ]; then
        echo "$(date): Client $IP nicht gefunden" >&2
        ERRORS=$((ERRORS + 1))
        continue
    fi

    UPDATED_DATA=$(echo "$CLIENT_DATA" | jq --argjson svcs "$SERVICES_JSON" '.blocked_services = (.blocked_services + $svcs | unique)')
    PAYLOAD=$(jq -n --arg name "$CLIENT_NAME" --argjson data "$UPDATED_DATA" '{"name": $name, "data": $data}')

    RESP_BODY=$(mktemp)
    HTTP_CODE=$(curl -s -w "%{http_code}" -o "$RESP_BODY" -u "$ADGUARD_USER:$ADGUARD_PASS" \
        -H "Content-Type: application/json" \
        -X POST "$ADGUARD_URL/control/clients/update" \
        -d "$PAYLOAD")

    if [ "$HTTP_CODE" = "200" ]; then
        echo "$(date): $SERVICES gesperrt fuer $CLIENT_NAME ($IP)"
    else
        echo "$(date): Fehler bei $CLIENT_NAME ($IP) - HTTP $HTTP_CODE: $(cat "$RESP_BODY")" >&2
        echo "$(date): Payload war: $PAYLOAD" >&2
        ERRORS=$((ERRORS + 1))
    fi
    rm -f "$RESP_BODY"
done

exit $ERRORS

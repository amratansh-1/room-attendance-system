#!/usr/bin/env bash
# End-to-end demo of the attendance API.
# Needs: the app running on localhost:8080 and a FRESH database (ids start at 1).
# Windows: run it from Git Bash. Usage:  bash scripts/demo.sh
BASE="${BASE:-http://localhost:8080}"
ADMIN="admin:${ADMIN_PASSWORD:-admin123}"
OBS1="obs1:observer123"
OBS2="obs2:observer123"

call() {  # call <user:pass> <METHOD> <path> [json-body]
  local user="$1" method="$2" path="$3" body="${4:-}"
  echo
  echo ">>> ${method} ${path}   (as ${user%%:*})"
  if [ -n "$body" ]; then
    curl -s -w "\nHTTP %{http_code}\n" -u "$user" -X "$method" \
         -H "Content-Type: application/json" -d "$body" "${BASE}${path}"
  else
    curl -s -w "\nHTTP %{http_code}\n" -u "$user" -X "$method" "${BASE}${path}"
  fi
}

echo "##### 1. Admin creates two observers (users 2 and 3; the bootstrap admin is user 1)"
call "$ADMIN" POST /api/admin/users '{"username":"obs1","password":"observer123","fullName":"Observer One","role":"OBSERVER"}'
call "$ADMIN" POST /api/admin/users '{"username":"obs2","password":"observer123","fullName":"Observer Two","role":"OBSERVER"}'

echo; echo "##### 2. Admin creates rooms (R101 holds 3 persons, R102 holds 2)"
call "$ADMIN" POST /api/admin/rooms '{"roomNo":"R101","capacity":3}'
call "$ADMIN" POST /api/admin/rooms '{"roomNo":"R102","capacity":2}'

echo; echo "##### 3. Admin allocates persons to rooms"
call "$ADMIN" POST /api/admin/rooms/1/persons/bulk '{"persons":[
  {"personCode":"S101","fullName":"Rahul Sharma"},
  {"personCode":"S102","fullName":"Neha Verma"},
  {"personCode":"S103","fullName":"Amit Singh"}]}'
call "$ADMIN" POST /api/admin/rooms/2/persons/bulk '{"persons":[
  {"personCode":"S201","fullName":"Priya Gupta"},
  {"personCode":"S202","fullName":"Karan Mehta"}]}'

echo; echo "##### 4. Admin assigns observers (obs1 -> R101, obs2 -> R102)"
call "$ADMIN" POST /api/admin/rooms/1/observers/2
call "$ADMIN" POST /api/admin/rooms/2/observers/3

echo; echo "##### 5. TRIGGER TEST: a 4th person in a full room is rejected by the database"
call "$ADMIN" POST /api/admin/rooms/1/persons '{"personCode":"S104","fullName":"Too Many"}'

echo; echo "##### 6. Observer 1 sees their room and marks attendance (any order)"
call "$OBS1" GET /api/observer/rooms
call "$OBS1" GET /api/observer/rooms/1/persons
call "$OBS1" POST /api/observer/attendance/bulk '{"entries":[
  {"personId":3,"status":"LATE"},
  {"personId":1,"status":"PRESENT"},
  {"personId":2,"status":"ABSENT"}]}'

echo; echo "##### 7. Observer 1 corrects S102: ABSENT -> LATE -> ABSENT (audit table records it)"
call "$OBS1" POST /api/observer/attendance '{"personId":2,"status":"LATE"}'
call "$OBS1" POST /api/observer/attendance '{"personId":2,"status":"ABSENT"}'

echo; echo "##### 8. SECURITY TEST: observer 1 tries to mark a person of room R102 (expect 403)"
call "$OBS1" POST /api/observer/attendance '{"personId":4,"status":"PRESENT"}'

echo; echo "##### 9. Observer 2 marks room R102"
call "$OBS2" POST /api/observer/attendance '{"personId":4,"status":"PRESENT"}'
call "$OBS2" POST /api/observer/attendance '{"personId":5,"status":"ABSENT"}'

echo; echo "##### 10. Admin report (reflects the marks immediately)"
call "$ADMIN" GET /api/admin/reports/attendance
echo; echo "----- same report as CSV -----"
curl -s -u "$ADMIN" "${BASE}/api/admin/reports/attendance/csv"

echo; echo "##### 11. Admin sees detailed rows: person, room, date, observer"
call "$ADMIN" GET /api/admin/attendance

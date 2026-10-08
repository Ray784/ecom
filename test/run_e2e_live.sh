#!/usr/bin/env bash
set -euo pipefail

# Live multi-server E2E verification script for ecommerce-spring-kafka.
# Remember D5: comments in this file intentionally avoid parenthetical punctuation.

REPO_ROOT="/usr/local/google/home/abhaybhat/ecommerce-spring-kafka"
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
JAVA_BIN="${JAVA_HOME}/bin/java"
LOG_DIR="/tmp/swarm_ecom1/e2e_servers"
PID_FILE="/tmp/swarm_ecom1/e2e_pids.txt"
INVOICE_DIR="/tmp/invoices"

mkdir -p "${LOG_DIR}"
mkdir -p "${INVOICE_DIR}"
rm -f "${PID_FILE}"

PASS_COUNT=0
FAIL_COUNT=0

cleanup() {
    echo ""
    echo "=== Cleaning up background microservice processes ==="
    if [[ -f "${PID_FILE}" ]]; then
        while read -r pid name; do
            if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
                echo "Stopping ${name} PID ${pid}"
                kill "${pid}" 2>/dev/null || true
            fi
        done < "${PID_FILE}"
        sleep 2
        while read -r pid name; do
            if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
                echo "Force killing ${name} PID ${pid}"
                kill -9 "${pid}" 2>/dev/null || true
            fi
        done < "${PID_FILE}"
        rm -f "${PID_FILE}"
    fi
}
trap cleanup EXIT

wait_for_port() {
    local port="$1"
    local name="$2"
    local max_wait="${3:-60}"
    local elapsed=0
    while ! bash -c "exec 3<>/dev/tcp/127.0.0.1/${port}" 2>/dev/null; do
        sleep 1
        elapsed=$((elapsed + 1))
        if [[ "${elapsed}" -ge "${max_wait}" ]]; then
            echo "ERROR: Timed out waiting for ${name} on port ${port}"
            if [[ -f "${LOG_DIR}/${name}.log" ]]; then
                tail -n 50 "${LOG_DIR}/${name}.log"
            fi
            exit 1
        fi
    done
    echo "READY: ${name} listening on port ${port} after ${elapsed}s"
}

assert_status() {
    local label="$1"
    local actual="$2"
    local expected="$3"
    local body="$4"
    if [[ "${actual}" == "${expected}" ]]; then
        echo "[PASS] ${label} -> HTTP ${actual}"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "[FAIL] ${label} -> expected HTTP ${expected}, got HTTP ${actual}. Body: ${body}"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        exit 1
    fi
}

assert_json_field() {
    local label="$1"
    local json="$2"
    local expr="$3"
    local expected="$4"
    local actual
    actual=$(python3 -c "import json, sys; data = json.loads(sys.stdin.read()); val = ${expr}; print(val)" <<< "${json}")
    if [[ "${actual}" == "${expected}" ]]; then
        echo "[PASS] ${label} -> ${actual}"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "[FAIL] ${label} -> expected '${expected}', got '${actual}'. Payload: ${json}"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        exit 1
    fi
}

echo "=== Step 1: Packaging executable Spring Boot jars ==="
mvn -f "${REPO_ROOT}/pom.xml" -B package -DskipTests > "${LOG_DIR}/mvn_package.log" 2>&1
echo "Maven package completed successfully."

echo "=== Step 2: Starting iam-service with Embedded Kafka Broker on port 9092 and HTTP 8081 ==="
"${JAVA_BIN}" \
    -Dembedded.kafka.broker.enabled=true \
    -Dembedded.kafka.broker.port=9092 \
    -jar "${REPO_ROOT}/iam-service/target/iam-service-1.0.0-SNAPSHOT.jar" \
    > "${LOG_DIR}/iam-service.log" 2>&1 &
echo "$! iam-service" >> "${PID_FILE}"

wait_for_port 9092 "embedded-kafka" 60
wait_for_port 8081 "iam-service" 60

echo "=== Step 3: Starting remaining 9 microservices ==="
declare -a SERVICES=(
    "user-service:8082"
    "search-service:8089"
    "product-service:8083"
    "order-service:8084"
    "payment-service:8085"
    "invoice-service:8086"
    "notification-service:8087"
    "review-service:8088"
    "api-gateway:8080"
)

for entry in "${SERVICES[@]}"; do
    svc="${entry%%:*}"
    port="${entry##*:}"
    "${JAVA_BIN}" \
        -jar "${REPO_ROOT}/${svc}/target/${svc}-1.0.0-SNAPSHOT.jar" \
        > "${LOG_DIR}/${svc}.log" 2>&1 &
    echo "$! ${svc}" >> "${PID_FILE}"
done

for entry in "${SERVICES[@]}"; do
    svc="${entry%%:*}"
    port="${entry##*:}"
    wait_for_port "${port}" "${svc}" 60
done

# Give Kafka consumer groups a brief moment to finish partition assignment
sleep 4

GW="http://localhost:8080"
IAM_URL="http://localhost:8081"

echo ""
echo "=== Step 4: Positive E2E API Flows via API Gateway on port 8080 ==="

# 4.1 Register Customer 1
echo "--- 4.1 Register Customer alice ---"
REG_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/users/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"alice","password":"Alice@1234","email":"alice@example.com","firstName":"Alice"}')
REG_BODY=$(echo "${REG_RESP}" | sed '$d')
REG_CODE=$(echo "${REG_RESP}" | tail -n1)
echo "Response: ${REG_BODY}"
assert_status "POST /users/register alice" "${REG_CODE}" "201" "${REG_BODY}"
ALICE_ID=$(python3 -c "import json,sys; print(json.loads(sys.stdin.read())['userId'])" <<< "${REG_BODY}")
echo "Alice userId=${ALICE_ID}"

# Register Customer 2 for cross-user access test
REG2_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/users/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"bob","password":"Bob@12345","email":"bob@example.com","firstName":"Bob"}')
REG2_BODY=$(echo "${REG2_RESP}" | sed '$d')
REG2_CODE=$(echo "${REG2_RESP}" | tail -n1)
assert_status "POST /users/register bob" "${REG2_CODE}" "201" "${REG2_BODY}"
BOB_ID=$(python3 -c "import json,sys; print(json.loads(sys.stdin.read())['userId'])" <<< "${REG2_BODY}")

# 4.2 Login as Seeded Admin and as Alice
echo "--- 4.2 Login Admin and Alice ---"
ADMIN_LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"Admin@1234"}')
ADMIN_LOGIN_BODY=$(echo "${ADMIN_LOGIN_RESP}" | sed '$d')
ADMIN_LOGIN_CODE=$(echo "${ADMIN_LOGIN_RESP}" | tail -n1)
echo "Admin Login Response: ${ADMIN_LOGIN_BODY}"
assert_status "POST /auth/login admin" "${ADMIN_LOGIN_CODE}" "200" "${ADMIN_LOGIN_BODY}"
ADMIN_TOKEN=$(python3 -c "import json,sys; print(json.loads(sys.stdin.read())['jwtToken'])" <<< "${ADMIN_LOGIN_BODY}")

ALICE_LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"alice","password":"Alice@1234"}')
ALICE_LOGIN_BODY=$(echo "${ALICE_LOGIN_RESP}" | sed '$d')
ALICE_LOGIN_CODE=$(echo "${ALICE_LOGIN_RESP}" | tail -n1)
echo "Alice Login Response: ${ALICE_LOGIN_BODY}"
assert_status "POST /auth/login alice" "${ALICE_LOGIN_CODE}" "200" "${ALICE_LOGIN_BODY}"
ALICE_TOKEN=$(python3 -c "import json,sys; print(json.loads(sys.stdin.read())['jwtToken'])" <<< "${ALICE_LOGIN_BODY}")

# 4.3 Validate JWT directly on IAM service
echo "--- 4.3 Validate JWT via POST http://localhost:8081/auth/validate ---"
VAL_RESP=$(curl -s -w "\n%{http_code}" -X POST "${IAM_URL}/auth/validate" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
VAL_BODY=$(echo "${VAL_RESP}" | sed '$d')
VAL_CODE=$(echo "${VAL_RESP}" | tail -n1)
echo "Validate Response: ${VAL_BODY}"
assert_status "POST /auth/validate" "${VAL_CODE}" "200" "${VAL_BODY}"
assert_json_field "JWT valid=true" "${VAL_BODY}" "data.get('valid')" "True"

# 4.4 Get & Update Customer Profile + Add Address via Gateway
echo "--- 4.4 Customer Profile & Shipping Address ---"
PROF_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/profiles/${ALICE_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
PROF_GET_BODY=$(echo "${PROF_GET_RESP}" | sed '$d')
PROF_GET_CODE=$(echo "${PROF_GET_RESP}" | tail -n1)
echo "GET Profile Response: ${PROF_GET_BODY}"
assert_status "GET /profiles/{userId}" "${PROF_GET_CODE}" "200" "${PROF_GET_BODY}"

PROF_PUT_RESP=$(curl -s -w "\n%{http_code}" -X PUT "${GW}/profiles/${ALICE_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"firstName":"Alice","lastName":"Wonderland","email":"alice@example.com"}')
PROF_PUT_BODY=$(echo "${PROF_PUT_RESP}" | sed '$d')
PROF_PUT_CODE=$(echo "${PROF_PUT_RESP}" | tail -n1)
echo "PUT Profile Response: ${PROF_PUT_BODY}"
assert_status "PUT /profiles/{userId}" "${PROF_PUT_CODE}" "200" "${PROF_PUT_BODY}"
assert_json_field "Profile lastName updated" "${PROF_PUT_BODY}" "data.get('lastName')" "Wonderland"

ADDR_POST_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/profiles/${ALICE_ID}/addresses" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"street":"742 Evergreen Terrace","city":"Springfield","zipCode":"62704","default":true}')
ADDR_POST_BODY=$(echo "${ADDR_POST_RESP}" | sed '$d')
ADDR_POST_CODE=$(echo "${ADDR_POST_RESP}" | tail -n1)
echo "POST Address Response: ${ADDR_POST_BODY}"
assert_status "POST /profiles/{userId}/addresses" "${ADDR_POST_CODE}" "201" "${ADDR_POST_BODY}"

# 4.5 Admin Creates Products, Updates Product, Gets Product via Gateway
echo "--- 4.5 Admin Catalog Management via Gateway ---"
PROD1_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/products" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"sku":"SKU-LAPTOP-01","name":"Pro Ultrabook 15","description":"High performance developer laptop","price":1200.00,"categoryId":"ELECTRONICS","initialStock":15,"imageUrl":"https://cdn.ecommerce.local/laptop01.png"}')
PROD1_BODY=$(echo "${PROD1_RESP}" | sed '$d')
PROD1_CODE=$(echo "${PROD1_RESP}" | tail -n1)
echo "Create Product 1 Response: ${PROD1_BODY}"
assert_status "POST /products SKU-LAPTOP-01" "${PROD1_CODE}" "201" "${PROD1_BODY}"

PROD2_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/products" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"sku":"SKU-MOUSE-02","name":"Wireless Ergonomic Mouse","description":"Bluetooth precision optical mouse","price":50.00,"categoryId":"ELECTRONICS","initialStock":30,"imageUrl":"https://cdn.ecommerce.local/mouse02.png"}')
PROD2_BODY=$(echo "${PROD2_RESP}" | sed '$d')
PROD2_CODE=$(echo "${PROD2_RESP}" | tail -n1)
echo "Create Product 2 Response: ${PROD2_BODY}"
assert_status "POST /products SKU-MOUSE-02" "${PROD2_CODE}" "201" "${PROD2_BODY}"

PROD1_UPD_RESP=$(curl -s -w "\n%{http_code}" -X PUT "${GW}/products/SKU-LAPTOP-01" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"sku":"SKU-LAPTOP-01","name":"Pro Ultrabook 15 Gen2","description":"Updated developer laptop with 32GB RAM","price":1250.00,"categoryId":"ELECTRONICS","initialStock":15,"imageUrl":"https://cdn.ecommerce.local/laptop01-v2.png"}')
PROD1_UPD_BODY=$(echo "${PROD1_UPD_RESP}" | sed '$d')
PROD1_UPD_CODE=$(echo "${PROD1_UPD_RESP}" | tail -n1)
echo "Update Product 1 Response: ${PROD1_UPD_BODY}"
assert_status "PUT /products/SKU-LAPTOP-01" "${PROD1_UPD_CODE}" "200" "${PROD1_UPD_BODY}"
assert_json_field "Updated product price" "${PROD1_UPD_BODY}" "float(data.get('price'))" "1250.0"

PROD1_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/products/SKU-LAPTOP-01")
PROD1_GET_BODY=$(echo "${PROD1_GET_RESP}" | sed '$d')
PROD1_GET_CODE=$(echo "${PROD1_GET_RESP}" | tail -n1)
echo "GET Product 1 Response: ${PROD1_GET_BODY}"
assert_status "GET /products/SKU-LAPTOP-01" "${PROD1_GET_CODE}" "200" "${PROD1_GET_BODY}"
assert_json_field "Initial stock is 15" "${PROD1_GET_BODY}" "data.get('currentStock')" "15"

# 4.6 Search Products via Gateway verifying auto-indexing into search-service
echo "--- 4.6 Search Products via Gateway ---"
SEARCH_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/search/products?q=Ultrabook&category=ELECTRONICS&sortField=price&sortOrder=asc")
SEARCH_BODY=$(echo "${SEARCH_RESP}" | sed '$d')
SEARCH_CODE=$(echo "${SEARCH_RESP}" | tail -n1)
echo "Search Response: ${SEARCH_BODY}"
assert_status "GET /search/products?q=Ultrabook" "${SEARCH_CODE}" "200" "${SEARCH_BODY}"
assert_json_field "Search totalElements=1" "${SEARCH_BODY}" "data['pagination']['totalElements']" "1"
assert_json_field "Search result SKU" "${SEARCH_BODY}" "data['results'][0]['sku']" "SKU-LAPTOP-01"

# 4.7 Add Items to Cart and View Cart via Gateway
echo "--- 4.7 Cart Management via Gateway ---"
CART_ADD1_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/carts/items" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"sku":"SKU-LAPTOP-01","quantity":1}')
CART_ADD1_BODY=$(echo "${CART_ADD1_RESP}" | sed '$d')
CART_ADD1_CODE=$(echo "${CART_ADD1_RESP}" | tail -n1)
echo "Add Cart Item 1 Response: ${CART_ADD1_BODY}"
assert_status "POST /carts/items SKU-LAPTOP-01" "${CART_ADD1_CODE}" "200" "${CART_ADD1_BODY}"

CART_ADD2_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/carts/items" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"sku":"SKU-MOUSE-02","quantity":2}')
CART_ADD2_BODY=$(echo "${CART_ADD2_RESP}" | sed '$d')
CART_ADD2_CODE=$(echo "${CART_ADD2_RESP}" | tail -n1)
echo "Add Cart Item 2 Response: ${CART_ADD2_BODY}"
assert_status "POST /carts/items SKU-MOUSE-02" "${CART_ADD2_CODE}" "200" "${CART_ADD2_BODY}"

CART_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/carts" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
CART_GET_BODY=$(echo "${CART_GET_RESP}" | sed '$d')
CART_GET_CODE=$(echo "${CART_GET_RESP}" | tail -n1)
echo "GET Cart Response: ${CART_GET_BODY}"
assert_status "GET /carts" "${CART_GET_CODE}" "200" "${CART_GET_BODY}"
assert_json_field "Cart subtotal=1350.0" "${CART_GET_BODY}" "float(data.get('subtotal'))" "1350.0"

# 4.8 Place Order via Gateway and Verify Asynchronous Kafka Saga Pipeline
echo "--- 4.8 Place Order and Verify Async Kafka Saga ---"
ORDER_POST_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/orders" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"shippingAddress":"742 Evergreen Terrace, Springfield, IL 62704"}')
ORDER_POST_BODY=$(echo "${ORDER_POST_RESP}" | sed '$d')
ORDER_POST_CODE=$(echo "${ORDER_POST_RESP}" | tail -n1)
echo "POST Order Response: ${ORDER_POST_BODY}"
assert_status "POST /orders" "${ORDER_POST_CODE}" "201" "${ORDER_POST_BODY}"
ORDER_ID=$(python3 -c "import json,sys; print(json.loads(sys.stdin.read())['orderId'])" <<< "${ORDER_POST_BODY}")
echo "Created Order ID: ${ORDER_ID}"

# Poll for async Kafka saga completion across Order, Product, Payment, Invoice, Notification
echo "Waiting for asynchronous Kafka event pipeline to complete for order ${ORDER_ID}..."
SAGA_DONE=0
for attempt in {1..20}; do
    sleep 1
    ORD_POLL=$(curl -s -H "Authorization: Bearer ${ALICE_TOKEN}" "${GW}/orders/${ORDER_ID}" || true)
    INV_POLL=$(curl -s -H "Authorization: Bearer ${ALICE_TOKEN}" "${GW}/invoices/${ORDER_ID}" || true)
    ORD_STATUS=$(python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(d.get('status',''))" <<< "${ORD_POLL}" 2>/dev/null || echo "")
    INV_ID=$(python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(d.get('id',''))" <<< "${INV_POLL}" 2>/dev/null || echo "")
    if [[ "${ORD_STATUS}" == "PAID" && -n "${INV_ID}" ]]; then
        echo "Kafka saga converged on attempt ${attempt}s: order status=${ORD_STATUS}, invoiceId=${INV_ID}"
        SAGA_DONE=1
        break
    fi
done

if [[ "${SAGA_DONE}" -ne 1 ]]; then
    echo "ERROR: Async Kafka saga did not converge within 20s"
    exit 1
fi

# Verify Order status is PAID
ORDER_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/orders/${ORDER_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
ORDER_GET_BODY=$(echo "${ORDER_GET_RESP}" | sed '$d')
ORDER_GET_CODE=$(echo "${ORDER_GET_RESP}" | tail -n1)
echo "GET Order after Saga: ${ORDER_GET_BODY}"
assert_status "GET /orders/{orderId}" "${ORDER_GET_CODE}" "200" "${ORDER_GET_BODY}"
assert_json_field "Order status is PAID" "${ORDER_GET_BODY}" "data.get('status')" "PAID"

# Verify Product Inventory Reserved
PROD_AFTER_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/products/SKU-LAPTOP-01")
PROD_AFTER_BODY=$(echo "${PROD_AFTER_RESP}" | sed '$d')
PROD_AFTER_CODE=$(echo "${PROD_AFTER_RESP}" | tail -n1)
echo "GET Product after Saga: ${PROD_AFTER_BODY}"
assert_status "GET /products/SKU-LAPTOP-01 after order" "${PROD_AFTER_CODE}" "200" "${PROD_AFTER_BODY}"
assert_json_field "Product reservedStock incremented to 1" "${PROD_AFTER_BODY}" "data.get('reservedStock')" "1"

# Verify Payment Transaction Status is SUCCESS
PAY_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/payments/order/${ORDER_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
PAY_GET_BODY=$(echo "${PAY_GET_RESP}" | sed '$d')
PAY_GET_CODE=$(echo "${PAY_GET_RESP}" | tail -n1)
echo "GET Payment Response: ${PAY_GET_BODY}"
assert_status "GET /payments/order/{orderId}" "${PAY_GET_CODE}" "200" "${PAY_GET_BODY}"
assert_json_field "Payment status is SUCCESS" "${PAY_GET_BODY}" "data.get('status')" "SUCCESS"

# Verify Invoice Generated and PDF exists on disk
INV_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/invoices/${ORDER_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
INV_GET_BODY=$(echo "${INV_GET_RESP}" | sed '$d')
INV_GET_CODE=$(echo "${INV_GET_RESP}" | tail -n1)
echo "GET Invoice Response: ${INV_GET_BODY}"
assert_status "GET /invoices/{orderId}" "${INV_GET_CODE}" "200" "${INV_GET_BODY}"
PDF_PATH=$(python3 -c "import json,sys; print(json.loads(sys.stdin.read())['pdfStorageLink'])" <<< "${INV_GET_BODY}")
if [[ -s "${PDF_PATH}" ]]; then
    echo "[PASS] Invoice PDF file exists and is non-empty at ${PDF_PATH}"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "[FAIL] Invoice PDF file missing at ${PDF_PATH}"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    exit 1
fi

# Verify Notification Logs for Order
NOTIF_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/notifications/order/${ORDER_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
NOTIF_GET_BODY=$(echo "${NOTIF_GET_RESP}" | sed '$d')
NOTIF_GET_CODE=$(echo "${NOTIF_GET_RESP}" | tail -n1)
echo "GET Notifications Response: ${NOTIF_GET_BODY}"
assert_status "GET /notifications/order/{orderId}" "${NOTIF_GET_CODE}" "200" "${NOTIF_GET_BODY}"
assert_json_field "Notifications count >= 2" "${NOTIF_GET_BODY}" "len(data) >= 2" "True"

# 4.9 Submit Product Review, Get Product Reviews, Get Aggregate Rating via Gateway
echo "--- 4.9 Product Reviews and Ratings via Gateway ---"
REV_POST_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/reviews" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"productId":"SKU-LAPTOP-01","rating":5,"comment":"Blazing fast laptop and keyboard feels great"}')
REV_POST_BODY=$(echo "${REV_POST_RESP}" | sed '$d')
REV_POST_CODE=$(echo "${REV_POST_RESP}" | tail -n1)
echo "POST Review Response: ${REV_POST_BODY}"
assert_status "POST /reviews" "${REV_POST_CODE}" "201" "${REV_POST_BODY}"

REV_LIST_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/reviews/products/SKU-LAPTOP-01")
REV_LIST_BODY=$(echo "${REV_LIST_RESP}" | sed '$d')
REV_LIST_CODE=$(echo "${REV_LIST_RESP}" | tail -n1)
echo "GET Product Reviews Response: ${REV_LIST_BODY}"
assert_status "GET /reviews/products/SKU-LAPTOP-01" "${REV_LIST_CODE}" "200" "${REV_LIST_BODY}"
assert_json_field "Review list count=1" "${REV_LIST_BODY}" "len(data)" "1"

REV_RATE_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/reviews/products/SKU-LAPTOP-01/rating")
REV_RATE_BODY=$(echo "${REV_RATE_RESP}" | sed '$d')
REV_RATE_CODE=$(echo "${REV_RATE_RESP}" | tail -n1)
echo "GET Product Rating Response: ${REV_RATE_BODY}"
assert_status "GET /reviews/products/SKU-LAPTOP-01/rating" "${REV_RATE_CODE}" "200" "${REV_RATE_BODY}"
assert_json_field "Average rating=5.0" "${REV_RATE_BODY}" "float(data.get('averageRating'))" "5.0"
assert_json_field "Review count=1" "${REV_RATE_BODY}" "data.get('reviewCount')" "1"

echo ""
echo "=== Step 5: Error Catalog Negative Cases Verification ==="

check_error_case() {
    local label="$1"
    local expected_http="$2"
    local expected_code="$3"
    local resp="$4"
    local body
    local code
    body=$(echo "${resp}" | sed '$d')
    code=$(echo "${resp}" | tail -n1)
    echo "Error Case [${expected_code}]: HTTP ${code} -> ${body}"
    assert_status "${label} HTTP status" "${code}" "${expected_http}" "${body}"
    assert_json_field "${label} errorCode=${expected_code}" "${body}" "data.get('errorCode')" "${expected_code}"
}

# IAM_4001: Bad password on login
ERR_IAM_4001=$(curl -s -w "\n%{http_code}" -X POST "${GW}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"alice","password":"WrongPassword123"}')
check_error_case "IAM_4001 bad login password" "401" "IAM_4001" "${ERR_IAM_4001}"

# IAM_4002: Missing/invalid JWT on protected route
ERR_IAM_4002=$(curl -s -w "\n%{http_code}" -X GET "${GW}/orders/${ORDER_ID}" \
    -H "Authorization: Bearer invalid.jwt.token")
check_error_case "IAM_4002 invalid JWT on protected route" "401" "IAM_4002" "${ERR_IAM_4002}"

# IAM_4004: Duplicate username registration
ERR_IAM_4004=$(curl -s -w "\n%{http_code}" -X POST "${GW}/users/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"alice","password":"Alice@1234","email":"alice2@example.com","firstName":"Alice"}')
check_error_case "IAM_4004 duplicate username" "400" "IAM_4004" "${ERR_IAM_4004}"

# IAM_4008: Customer calling admin endpoint POST /products
ERR_IAM_4008=$(curl -s -w "\n%{http_code}" -X POST "${GW}/products" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"sku":"SKU-UNAUTH","name":"Unauthorized Item","price":10.00,"categoryId":"BOOKS","initialStock":5}')
check_error_case "IAM_4008 customer calling admin POST /products" "403" "IAM_4008" "${ERR_IAM_4008}"

# USER_4007: Customer modifying another user's profile
ERR_USER_4007=$(curl -s -w "\n%{http_code}" -X PUT "${GW}/profiles/${BOB_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"firstName":"Hacked","lastName":"Name","email":"bob@example.com"}')
check_error_case "USER_4007 modifying another user profile" "403" "USER_4007" "${ERR_USER_4007}"

# PROD_4001: Unknown product SKU
ERR_PROD_4001=$(curl -s -w "\n%{http_code}" -X GET "${GW}/products/SKU-DOES-NOT-EXIST")
check_error_case "PROD_4001 unknown SKU" "404" "PROD_4001" "${ERR_PROD_4001}"

# PROD_4002: Insufficient stock on inventory reservation
ERR_PROD_4002=$(curl -s -w "\n%{http_code}" -X POST "http://localhost:8083/inventory/reserve" \
    -H "Content-Type: application/json" \
    -d '{"orderId":"ORD-OVERSTOCK-1","items":[{"sku":"SKU-LAPTOP-01","quantity":999,"unitPrice":1250.00}]}')
check_error_case "PROD_4002 insufficient stock" "400" "PROD_4002" "${ERR_PROD_4002}"

# ORDER_4001: Empty cart order creation
ERR_ORDER_4001=$(curl -s -w "\n%{http_code}" -X POST "${GW}/orders" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"shippingAddress":"742 Evergreen Terrace"}')
check_error_case "ORDER_4001 empty cart order" "400" "ORDER_4001" "${ERR_ORDER_4001}"

# INV_4001: Missing invoice for non-existent order
ERR_INV_4001=$(curl -s -w "\n%{http_code}" -X GET "${GW}/invoices/ORD-NONEXISTENT-999" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
check_error_case "INV_4001 missing invoice" "404" "INV_4001" "${ERR_INV_4001}"

# REVIEW_4001: Prohibited word in review comment
ERR_REVIEW_4001=$(curl -s -w "\n%{http_code}" -X POST "${GW}/reviews" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"productId":"SKU-MOUSE-02","rating":3,"comment":"This item is spam and fake"}')
check_error_case "REVIEW_4001 prohibited word in review" "400" "REVIEW_4001" "${ERR_REVIEW_4001}"

# REVIEW_4002: Duplicate review by Alice on SKU-LAPTOP-01
ERR_REVIEW_4002=$(curl -s -w "\n%{http_code}" -X POST "${GW}/reviews" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"productId":"SKU-LAPTOP-01","rating":4,"comment":"Second review attempt on same laptop"}')
check_error_case "REVIEW_4002 duplicate review" "409" "REVIEW_4002" "${ERR_REVIEW_4002}"

# REVIEW_4003: Rating out of range
ERR_REVIEW_4003=$(curl -s -w "\n%{http_code}" -X POST "${GW}/reviews" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"productId":"SKU-MOUSE-02","rating":9,"comment":"Invalid rating value"}')
check_error_case "REVIEW_4003 rating out of range" "400" "REVIEW_4003" "${ERR_REVIEW_4003}"

# SEARCH_4001: Search query too short
ERR_SEARCH_4001=$(curl -s -w "\n%{http_code}" -X GET "${GW}/search/products?q=a")
check_error_case "SEARCH_4001 query too short" "400" "SEARCH_4001" "${ERR_SEARCH_4001}"

echo ""
echo "=== E2E Verification Summary: ${PASS_COUNT} assertions passed, ${FAIL_COUNT} failed ==="

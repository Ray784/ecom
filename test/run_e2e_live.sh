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

# 4.2 Login as Seeded Admin, Alice, and Bob
echo "--- 4.2 Login Admin, Alice, and Bob ---"
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

BOB_LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"bob","password":"Bob@12345"}')
BOB_LOGIN_BODY=$(echo "${BOB_LOGIN_RESP}" | sed '$d')
BOB_LOGIN_CODE=$(echo "${BOB_LOGIN_RESP}" | tail -n1)
assert_status "POST /auth/login bob" "${BOB_LOGIN_CODE}" "200" "${BOB_LOGIN_BODY}"
BOB_TOKEN=$(python3 -c "import json,sys; print(json.loads(sys.stdin.read())['jwtToken'])" <<< "${BOB_LOGIN_BODY}")

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
    PROD_POLL=$(curl -s "${GW}/products/SKU-LAPTOP-01" || true)
    ORD_STATUS=$(python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(d.get('status',''))" <<< "${ORD_POLL}" 2>/dev/null || echo "")
    INV_ID=$(python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(d.get('id',''))" <<< "${INV_POLL}" 2>/dev/null || echo "")
    PROD_STOCK=$(python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(d.get('currentStock',''))" <<< "${PROD_POLL}" 2>/dev/null || echo "")
    if [[ "${ORD_STATUS}" == "PAID" && -n "${INV_ID}" && "${PROD_STOCK}" == "14" ]]; then
        echo "Kafka saga converged on attempt ${attempt}s: order status=${ORD_STATUS}, invoiceId=${INV_ID}, currentStock=${PROD_STOCK}"
        SAGA_DONE=1
        break
    fi
done

if [[ "${SAGA_DONE}" -ne 1 ]]; then
    echo "ERROR: Async Kafka saga did not converge within 20s"
    exit 1
fi

# Verify Order status is PAID and Verified Purchase endpoint reports true for Alice and false for Bob
ORDER_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/orders/${ORDER_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
ORDER_GET_BODY=$(echo "${ORDER_GET_RESP}" | sed '$d')
ORDER_GET_CODE=$(echo "${ORDER_GET_RESP}" | tail -n1)
echo "GET Order after Saga: ${ORDER_GET_BODY}"
assert_status "GET /orders/{orderId}" "${ORDER_GET_CODE}" "200" "${ORDER_GET_BODY}"
assert_json_field "Order status is PAID" "${ORDER_GET_BODY}" "data.get('status')" "PAID"

VP_ALICE_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/orders/verified-purchase?userId=${ALICE_ID}&sku=SKU-LAPTOP-01" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
VP_ALICE_BODY=$(echo "${VP_ALICE_RESP}" | sed '$d')
VP_ALICE_CODE=$(echo "${VP_ALICE_RESP}" | tail -n1)
echo "GET Verified Purchase Alice Response: ${VP_ALICE_BODY}"
assert_status "GET /orders/verified-purchase alice" "${VP_ALICE_CODE}" "200" "${VP_ALICE_BODY}"
assert_json_field "Alice verified purchase=True" "${VP_ALICE_BODY}" "data.get('verified')" "True"

VP_BOB_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/orders/verified-purchase?userId=${BOB_ID}&sku=SKU-LAPTOP-01" \
    -H "Authorization: Bearer ${BOB_TOKEN}")
VP_BOB_BODY=$(echo "${VP_BOB_RESP}" | sed '$d')
VP_BOB_CODE=$(echo "${VP_BOB_RESP}" | tail -n1)
assert_status "GET /orders/verified-purchase bob" "${VP_BOB_CODE}" "200" "${VP_BOB_BODY}"
assert_json_field "Bob verified purchase=False" "${VP_BOB_BODY}" "data.get('verified')" "False"

# Verify Product Inventory Post-Payment Stock Decrement: currentStock=14, reservedStock=0
PROD_AFTER_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/products/SKU-LAPTOP-01")
PROD_AFTER_BODY=$(echo "${PROD_AFTER_RESP}" | sed '$d')
PROD_AFTER_CODE=$(echo "${PROD_AFTER_RESP}" | tail -n1)
echo "GET Product after Saga: ${PROD_AFTER_BODY}"
assert_status "GET /products/SKU-LAPTOP-01 after order" "${PROD_AFTER_CODE}" "200" "${PROD_AFTER_BODY}"
assert_json_field "Product currentStock decremented to 14" "${PROD_AFTER_BODY}" "data.get('currentStock')" "14"
assert_json_field "Product reservedStock settled to 0" "${PROD_AFTER_BODY}" "data.get('reservedStock')" "0"

PROD2_AFTER_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/products/SKU-MOUSE-02")
PROD2_AFTER_BODY=$(echo "${PROD2_AFTER_RESP}" | sed '$d')
PROD2_AFTER_CODE=$(echo "${PROD2_AFTER_RESP}" | tail -n1)
assert_status "GET /products/SKU-MOUSE-02 after order" "${PROD2_AFTER_CODE}" "200" "${PROD2_AFTER_BODY}"
assert_json_field "Product 2 currentStock decremented to 28" "${PROD2_AFTER_BODY}" "data.get('currentStock')" "28"
assert_json_field "Product 2 reservedStock settled to 0" "${PROD2_AFTER_BODY}" "data.get('reservedStock')" "0"

# Verify Payment Transaction Status is SUCCESS with Razorpay pay_ and order_ identifiers
PAY_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/payments/order/${ORDER_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
PAY_GET_BODY=$(echo "${PAY_GET_RESP}" | sed '$d')
PAY_GET_CODE=$(echo "${PAY_GET_RESP}" | tail -n1)
echo "GET Payment Response: ${PAY_GET_BODY}"
assert_status "GET /payments/order/{orderId}" "${PAY_GET_CODE}" "200" "${PAY_GET_BODY}"
assert_json_field "Payment status is SUCCESS" "${PAY_GET_BODY}" "data.get('status')" "SUCCESS"
assert_json_field "Payment gatewayRef starts with pay_" "${PAY_GET_BODY}" "str(data.get('gatewayRef','')).startswith('pay_')" "True"
assert_json_field "Payment razorpayOrderId starts with order_" "${PAY_GET_BODY}" "str(data.get('razorpayOrderId','')).startswith('order_')" "True"

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

# 4.10 Fake Razorpay Emulator Endpoints and HMAC-SHA256 Webhook Verification via Gateway
echo "--- 4.10 Fake Razorpay Emulator & Webhook Verification via Gateway ---"
RZP_UNAUTH_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/v1/orders" \
    -H "Content-Type: application/json" \
    -d '{"amount":50000,"currency":"INR","receipt":"rcpt-unauth"}')
RZP_UNAUTH_BODY=$(echo "${RZP_UNAUTH_RESP}" | sed '$d')
RZP_UNAUTH_CODE=$(echo "${RZP_UNAUTH_RESP}" | tail -n1)
echo "Razorpay Unauth Order Response: ${RZP_UNAUTH_BODY}"
assert_status "POST /v1/orders without Basic Auth" "${RZP_UNAUTH_CODE}" "400" "${RZP_UNAUTH_BODY}"
assert_json_field "Razorpay unauth errorCode=PAY_4002" "${RZP_UNAUTH_BODY}" "data.get('errorCode')" "PAY_4002"

RZP_ORD_RESP=$(curl -s -w "\n%{http_code}" -u "rzp_test_key_id:rzp_test_key_secret" \
    -X POST "${GW}/v1/orders" \
    -H "Content-Type: application/json" \
    -d '{"amount":50000,"currency":"INR","receipt":"rcpt-e2e-1"}')
RZP_ORD_BODY=$(echo "${RZP_ORD_RESP}" | sed '$d')
RZP_ORD_CODE=$(echo "${RZP_ORD_RESP}" | tail -n1)
echo "Razorpay Create Order Response: ${RZP_ORD_BODY}"
assert_status "POST /v1/orders with Basic Auth" "${RZP_ORD_CODE}" "200" "${RZP_ORD_BODY}"
assert_json_field "Razorpay order id starts with order_" "${RZP_ORD_BODY}" "str(data.get('id','')).startswith('order_')" "True"
assert_json_field "Razorpay order status=created" "${RZP_ORD_BODY}" "data.get('status')" "created"
RZP_ORDER_ID=$(python3 -c "import json,sys; print(json.loads(sys.stdin.read())['id'])" <<< "${RZP_ORD_BODY}")

RZP_ORD_GET_RESP=$(curl -s -w "\n%{http_code}" -u "rzp_test_key_id:rzp_test_key_secret" \
    -X GET "${GW}/v1/orders/${RZP_ORDER_ID}")
RZP_ORD_GET_BODY=$(echo "${RZP_ORD_GET_RESP}" | sed '$d')
RZP_ORD_GET_CODE=$(echo "${RZP_ORD_GET_RESP}" | tail -n1)
assert_status "GET /v1/orders/{id}" "${RZP_ORD_GET_CODE}" "200" "${RZP_ORD_GET_BODY}"
assert_json_field "GET /v1/orders/{id} id matches" "${RZP_ORD_GET_BODY}" "data.get('id')" "${RZP_ORDER_ID}"

RZP_PAY_RESP=$(curl -s -w "\n%{http_code}" -u "rzp_test_key_id:rzp_test_key_secret" \
    -X POST "${GW}/v1/payments" \
    -H "Content-Type: application/json" \
    -d "{\"order_id\":\"${RZP_ORDER_ID}\",\"amount\":50000,\"method\":\"card\",\"payer_user_id\":\"${ALICE_ID}\"}")
RZP_PAY_BODY=$(echo "${RZP_PAY_RESP}" | sed '$d')
RZP_PAY_CODE=$(echo "${RZP_PAY_RESP}" | tail -n1)
echo "Razorpay Create Payment Response: ${RZP_PAY_BODY}"
assert_status "POST /v1/payments with Basic Auth" "${RZP_PAY_CODE}" "200" "${RZP_PAY_BODY}"
assert_json_field "Razorpay payment id starts with pay_" "${RZP_PAY_BODY}" "str(data.get('id','')).startswith('pay_')" "True"
assert_json_field "Razorpay payment status=captured" "${RZP_PAY_BODY}" "data.get('status')" "captured"
RZP_PAY_ID=$(python3 -c "import json,sys; print(json.loads(sys.stdin.read())['id'])" <<< "${RZP_PAY_BODY}")

RZP_PAY_GET_RESP=$(curl -s -w "\n%{http_code}" -u "rzp_test_key_id:rzp_test_key_secret" \
    -X GET "${GW}/v1/payments/${RZP_PAY_ID}")
RZP_PAY_GET_BODY=$(echo "${RZP_PAY_GET_RESP}" | sed '$d')
RZP_PAY_GET_CODE=$(echo "${RZP_PAY_GET_RESP}" | tail -n1)
assert_status "GET /v1/payments/{id}" "${RZP_PAY_GET_CODE}" "200" "${RZP_PAY_GET_BODY}"
assert_json_field "GET /v1/payments/{id} status=captured" "${RZP_PAY_GET_BODY}" "data.get('status')" "captured"

WEBHOOK_PAYLOAD="{\"event\":\"payment.captured\",\"orderId\":\"${ORDER_ID}\"}"
WEBHOOK_BAD_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/payments/razorpay/webhook" \
    -H "Content-Type: application/json" \
    -H "X-Razorpay-Signature: deadbeef" \
    -d "${WEBHOOK_PAYLOAD}")
WEBHOOK_BAD_BODY=$(echo "${WEBHOOK_BAD_RESP}" | sed '$d')
WEBHOOK_BAD_CODE=$(echo "${WEBHOOK_BAD_RESP}" | tail -n1)
echo "Razorpay Webhook Bad Signature Response: ${WEBHOOK_BAD_BODY}"
assert_status "POST /payments/razorpay/webhook bad signature" "${WEBHOOK_BAD_CODE}" "400" "${WEBHOOK_BAD_BODY}"
assert_json_field "Razorpay webhook bad signature errorCode=PAY_4002" "${WEBHOOK_BAD_BODY}" "data.get('errorCode')" "PAY_4002"

WEBHOOK_SIG=$(python3 -c "import hmac, hashlib, sys; print(hmac.new(b'rzp_test_webhook_secret', sys.argv[1].encode('utf-8'), hashlib.sha256).hexdigest())" "${WEBHOOK_PAYLOAD}")
WEBHOOK_OK_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/payments/razorpay/webhook" \
    -H "Content-Type: application/json" \
    -H "X-Razorpay-Signature: ${WEBHOOK_SIG}" \
    -d "${WEBHOOK_PAYLOAD}")
WEBHOOK_OK_BODY=$(echo "${WEBHOOK_OK_RESP}" | sed '$d')
WEBHOOK_OK_CODE=$(echo "${WEBHOOK_OK_RESP}" | tail -n1)
echo "Razorpay Webhook Valid Signature Response: ${WEBHOOK_OK_BODY}"
assert_status "POST /payments/razorpay/webhook valid signature" "${WEBHOOK_OK_CODE}" "200" "${WEBHOOK_OK_BODY}"
assert_json_field "Razorpay webhook paymentStatus=SUCCESS" "${WEBHOOK_OK_BODY}" "data.get('paymentStatus')" "SUCCESS"

# 4.11 Payment-Failed Compensation Saga & Reserved Stock Release Verification
echo "--- 4.11 Payment-Failed Compensation Saga & Reserved Stock Release ---"
DEC_CART_RESP=$(curl -s -w "\n%{http_code}" -X POST "http://localhost:8084/carts/items" \
    -H "X-User-Id: user-decline-01" \
    -H "Content-Type: application/json" \
    -d '{"sku":"SKU-LAPTOP-01","quantity":2}')
DEC_CART_BODY=$(echo "${DEC_CART_RESP}" | sed '$d')
DEC_CART_CODE=$(echo "${DEC_CART_RESP}" | tail -n1)
assert_status "POST /carts/items for user-decline-01" "${DEC_CART_CODE}" "200" "${DEC_CART_BODY}"

DEC_ORD_RESP=$(curl -s -w "\n%{http_code}" -X POST "http://localhost:8084/orders" \
    -H "X-User-Id: user-decline-01" \
    -H "Content-Type: application/json" \
    -d '{"shippingAddress":"1313 Mockingbird Lane, Springfield"}')
DEC_ORD_BODY=$(echo "${DEC_ORD_RESP}" | sed '$d')
DEC_ORD_CODE=$(echo "${DEC_ORD_RESP}" | tail -n1)
echo "POST Declined User Order Response: ${DEC_ORD_BODY}"
assert_status "POST /orders for user-decline-01" "${DEC_ORD_CODE}" "201" "${DEC_ORD_BODY}"
DECLINE_ORDER_ID=$(python3 -c "import json,sys; print(json.loads(sys.stdin.read())['orderId'])" <<< "${DEC_ORD_BODY}")

echo "Waiting for declined payment compensation saga on order ${DECLINE_ORDER_ID}..."
DEC_SAGA_DONE=0
for attempt in {1..20}; do
    sleep 1
    DEC_ORD_POLL=$(curl -s -H "Authorization: Bearer ${ALICE_TOKEN}" "${GW}/orders/${DECLINE_ORDER_ID}" || true)
    DEC_PROD_POLL=$(curl -s "${GW}/products/SKU-LAPTOP-01" || true)
    DEC_STATUS=$(python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(d.get('status',''))" <<< "${DEC_ORD_POLL}" 2>/dev/null || echo "")
    DEC_RES=$(python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(d.get('reservedStock',''))" <<< "${DEC_PROD_POLL}" 2>/dev/null || echo "")
    if [[ "${DEC_STATUS}" == "FAILED" && "${DEC_RES}" == "0" ]]; then
        echo "Declined payment saga converged on attempt ${attempt}s: order status=${DEC_STATUS}, reservedStock=${DEC_RES}"
        DEC_SAGA_DONE=1
        break
    fi
done

if [[ "${DEC_SAGA_DONE}" -ne 1 ]]; then
    echo "ERROR: Declined payment compensation saga did not converge within 20s"
    exit 1
fi

DEC_ORD_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/orders/${DECLINE_ORDER_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
DEC_ORD_GET_BODY=$(echo "${DEC_ORD_GET_RESP}" | sed '$d')
DEC_ORD_GET_CODE=$(echo "${DEC_ORD_GET_RESP}" | tail -n1)
echo "GET Declined Order after Saga: ${DEC_ORD_GET_BODY}"
assert_status "GET /orders/{declineOrderId}" "${DEC_ORD_GET_CODE}" "200" "${DEC_ORD_GET_BODY}"
assert_json_field "Declined order status is FAILED" "${DEC_ORD_GET_BODY}" "data.get('status')" "FAILED"

DEC_PAY_GET_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/payments/order/${DECLINE_ORDER_ID}" \
    -H "Authorization: Bearer ${ALICE_TOKEN}")
DEC_PAY_GET_BODY=$(echo "${DEC_PAY_GET_RESP}" | sed '$d')
DEC_PAY_GET_CODE=$(echo "${DEC_PAY_GET_RESP}" | tail -n1)
echo "GET Declined Payment Response: ${DEC_PAY_GET_BODY}"
assert_status "GET /payments/order/{declineOrderId}" "${DEC_PAY_GET_CODE}" "200" "${DEC_PAY_GET_BODY}"
assert_json_field "Declined payment status is FAILED" "${DEC_PAY_GET_BODY}" "data.get('status')" "FAILED"
assert_json_field "Declined payment gatewayRef starts with pay_" "${DEC_PAY_GET_BODY}" "str(data.get('gatewayRef','')).startswith('pay_')" "True"

DEC_PROD_RESP=$(curl -s -w "\n%{http_code}" -X GET "${GW}/products/SKU-LAPTOP-01")
DEC_PROD_BODY=$(echo "${DEC_PROD_RESP}" | sed '$d')
DEC_PROD_CODE=$(echo "${DEC_PROD_RESP}" | tail -n1)
echo "GET Product after Declined Order Saga: ${DEC_PROD_BODY}"
assert_status "GET /products/SKU-LAPTOP-01 after declined order" "${DEC_PROD_CODE}" "200" "${DEC_PROD_BODY}"
assert_json_field "Product currentStock remains 14 after release" "${DEC_PROD_BODY}" "data.get('currentStock')" "14"
assert_json_field "Product reservedStock released back to 0" "${DEC_PROD_BODY}" "data.get('reservedStock')" "0"

echo ""
echo "=== Step 5: Error Catalog Negative Cases, Verified Purchaser & Password RBAC ==="

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

# REVIEW_4004: Review submitted for non-existent product SKU-GHOST-999
ERR_REVIEW_4004=$(curl -s -w "\n%{http_code}" -X POST "${GW}/reviews" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"productId":"SKU-GHOST-999","rating":5,"comment":"Review for non-existent product"}')
check_error_case "REVIEW_4004 non-existent product review" "404" "REVIEW_4004" "${ERR_REVIEW_4004}"

# REVIEW_4006: Unverified purchaser Bob submitting review for SKU-LAPTOP-01
ERR_REVIEW_4006=$(curl -s -w "\n%{http_code}" -X POST "${GW}/reviews" \
    -H "Authorization: Bearer ${BOB_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"productId":"SKU-LAPTOP-01","rating":4,"comment":"Bob has not purchased this laptop"}')
check_error_case "REVIEW_4006 unverified purchaser review" "403" "REVIEW_4006" "${ERR_REVIEW_4006}"

# SEARCH_4001: Search query too short
ERR_SEARCH_4001=$(curl -s -w "\n%{http_code}" -X GET "${GW}/search/products?q=a")
check_error_case "SEARCH_4001 query too short" "400" "SEARCH_4001" "${ERR_SEARCH_4001}"

# 5.2 Gateway Password Update & Ownership Check: PUT /users/{id}/password
echo "--- 5.2 Gateway Password Update & Ownership Check ---"
ERR_PWD_IAM_4008=$(curl -s -w "\n%{http_code}" -X PUT "${GW}/users/${BOB_ID}/password" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"newPassword":"HackedBob@1234"}')
check_error_case "IAM_4008 customer updating another user password" "403" "IAM_4008" "${ERR_PWD_IAM_4008}"

PWD_SELF_RESP=$(curl -s -w "\n%{http_code}" -X PUT "${GW}/users/${ALICE_ID}/password" \
    -H "Authorization: Bearer ${ALICE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"newPassword":"AliceNew@5678"}')
PWD_SELF_BODY=$(echo "${PWD_SELF_RESP}" | sed '$d')
PWD_SELF_CODE=$(echo "${PWD_SELF_RESP}" | tail -n1)
echo "PUT Self Password Response: ${PWD_SELF_BODY}"
assert_status "PUT /users/{aliceId}/password self" "${PWD_SELF_CODE}" "200" "${PWD_SELF_BODY}"
assert_json_field "Password update success=True" "${PWD_SELF_BODY}" "data.get('success')" "True"

ALICE_RELOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GW}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"alice","password":"AliceNew@5678"}')
ALICE_RELOGIN_BODY=$(echo "${ALICE_RELOGIN_RESP}" | sed '$d')
ALICE_RELOGIN_CODE=$(echo "${ALICE_RELOGIN_RESP}" | tail -n1)
echo "Alice Re-Login with New Password Response: ${ALICE_RELOGIN_BODY}"
assert_status "POST /auth/login alice with new password" "${ALICE_RELOGIN_CODE}" "200" "${ALICE_RELOGIN_BODY}"
assert_json_field "Re-login issued fresh JWT" "${ALICE_RELOGIN_BODY}" "bool(data.get('jwtToken'))" "True"

echo ""
echo "=== E2E Verification Summary: ${PASS_COUNT} assertions passed, ${FAIL_COUNT} failed ==="

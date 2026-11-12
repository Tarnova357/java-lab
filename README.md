# Лабораторна робота №5 — Асинхронна обробка

## Реалізовані механізми асинхронності та їх пояснення

### Налаштування пулів потоків та обробка винятків (`java_lab/config/AsyncConfig.java`)

У конфігурації визначено власний виконавець ThreadPoolTaskExecutor з обмеженими ресурсами, місткістю черги та префіксом назви потоків asset-async- для виконання основних асинхронних операцій. Окремо налаштовано ізольований малий пул limitedOverflowExecutor з одним потоком та одним місцем у черзі для відпрацювання сценаріїв перевантаження. Для методів із типом повернення void реалізовано AsyncUncaughtExceptionHandler, який перехоплює та фіксує помилки у фонових потоках.

### Фонова генерація підписаних ZIP-квитанцій Fire-and-Forget (`java_lab/service/OrderLogisticsAsyncService.java` та `java_lab/service/DeliveryOrderService.java`)

Метод generateSignedReceiptArchive виконується асинхронно у потоці пулу asset-async- без блокування основного запиту. Після збереження замовлення у методі create класу DeliveryOrderService у фоні формується текстова квитанція, обчислюється криптографічний підпис HmacSHA256 та створюється ZIP-архів на диску. Клієнт отримує відповідь одразу, не чекаючи завершення операцій вводу-виводу.

### Паралельне виконання запитів та об'єднання результатів (`java_lab/service/OrderLogisticsAsyncService.java` та `java_lab/service/DeliveryOrderService.java`)

Два незалежні асинхронні методи fetchLiveUsdRateFromNbu та verifyKeycloakOidcHealth повертають CompletableFuture та виконують одночасні HTTP-запити до API Національного банку України та Keycloak OIDC. У методі calculateOrderSettlement класу DeliveryOrderService результати обох викликів об'єднуються через thenCombine у підсумковий фінансовий розрахунок замовлення. Очікування кожного Future обмежене в часі через orTimeout, а у разі тайм-ауту чи мережевого збою спрацьовують exceptionally та handle, повертаючи резервні значення без падіння запиту.

### Обробка переповнення черги виконавця (`java_lab/exception/GlobalExceptionHandler.java` та `java_lab/controller/DeliveryOrderController.java`)

У контролері DeliveryOrderController додано ендпоінти для отримання фінансового розрахунку замовлення та перевірки навантаження обмеженого пулу потоків. При надсиланні більшої кількості задач, ніж може вмістити малий пул та його черга, виникає TaskRejectedException. Цей виняток цілеспрямовано перехоплюється у GlobalExceptionHandler та повертає клієнту контрольовану відповідь зі статусом 503 Service Unavailable замість помилки 500.

### Тести

`AsyncProcessingTest.java` — модульні та інтеграційні тести з локальним HttpServer
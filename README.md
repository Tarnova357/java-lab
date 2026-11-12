# Лабораторна робота №4 — AOP (Aspect-Oriented Programming)

## Додані аспекти 

### Перевірка активності сховища (`java_lab/annotation/VerifyActiveStorage.java` та `java_lab/aspect/ActiveStorageValidationAspect.java`)

Перед оформленням замовлення аспект перевіряє статус акаунта-сховища StorageAccount, до якого прив'язаний віртуальний актив. Якщо акаунт має статус FROZEN або BANNED, аспект блокує виконання методу proceed і викидає виняток IllegalArgumentException. Застосовано до методу create у класі DeliveryOrderService.

### Ідемпотентність запитів (`java_lab/annotation/IdempotentRequest.java` та `java_lab/aspect/IdempotencyAspect.java`)

Аспект захищає від повторного створення однакових замовлень при дублюванні заголовка Idempotency-Key. Перший запит виконується і кешується, а при повторному запиті з тим самим ключем аспект одразу повертає закешований результат без повторного виклику методу. Застосовано до методу create у класі DeliveryOrderController.

### Маскування нікнеймів клієнтів (`java_lab/annotation/MaskCustomerTag.java` та `java_lab/aspect/CustomerTagMaskingAspect.java`)

Після отримання списку замовлень аспект перевіряє роль поточного користувача. Якщо це звичайний USER, поле customerTag частково приховується, наприклад TEST_PLAYER перетворюється на TE*******ER. Якщо роль ADMIN, повертаються повні нікнейми без змін. Застосовано до методів getAll та getById у класі DeliveryOrderService. Для демонстрації проблеми самовиклику у класі DeliveryOrderService додано метод getAllViaSelfInvocation, який викликає this.getAll напряму всередині класу в обхід Spring-проксі, через що аспект не спрацьовує без жодних помилок.


### Тести

`AspectsUnitTest.java` — модульні тести для всіх трьох аспектів 
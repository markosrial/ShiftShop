ALTER TABLE User
    ADD name VARCHAR(30) NOT NULL AFTER password,
    ADD surnames VARCHAR(60) NOT NULL AFTER name,
    ADD creationTimestamp DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD updateTimestamp DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);

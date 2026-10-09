
CREATE USER IF NOT EXISTS 'crediya_app'@'localhost'
  IDENTIFIED BY 'CAMBIE_ESTA_CLAVE';

ALTER USER 'crediya_app'@'localhost'
  IDENTIFIED BY 'CAMBIE_ESTA_CLAVE';

GRANT SELECT, INSERT, UPDATE, DELETE ON crediya_db.* TO 'crediya_app'@'localhost';

FLUSH PRIVILEGES;

SELECT 'Usuario crediya_app listo.' AS resultado;

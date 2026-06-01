# TODO

## Concurrencia: fix de estructuras compartidas
- [ ] Cambiar `reviveEnCurso` de `HashMap` a `ConcurrentHashMap` en `GameEngine`.
- [ ] Evaluar y ajustar `puertas` para evitar iteración concurrente; cambiar a `CopyOnWriteArrayList` (o alternativa) en `GameEngine`.
- [ ] Ejecutar tests/build (mvn test) para validar.


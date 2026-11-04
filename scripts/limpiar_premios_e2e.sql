-- Solo fixtures de la ejecución indicada por run_e2e.sh; nunca usar el DELETE de asociaciones de BackVynils.
BEGIN;
CREATE TEMP TABLE owned_prizes AS
SELECT id FROM prize
WHERE name LIKE 'HU14 E2E ' || :'hu14_run' || ' %'
  AND description = 'Dato HU14' AND organization = 'Equipo de pruebas HU14';
CREATE TEMP TABLE owned_performers AS
SELECT id FROM performer
WHERE name LIKE 'HU14 E2E ' || :'hu14_run' || ' %'
  AND description = 'Artista de prueba HU14';

-- Ante una relación ajena a estos fixtures, abortar en lugar de borrar datos compartidos.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM performer_prize
        WHERE ("prizeId" IN (SELECT id FROM owned_prizes)
            OR "performerId" IN (SELECT id FROM owned_performers))
          AND NOT ("prizeId" IN (SELECT id FROM owned_prizes)
            AND "performerId" IN (SELECT id FROM owned_performers))
    ) THEN
        RAISE EXCEPTION 'Los fixtures HU14 tienen asociaciones ajenas; no se elimina ningún dato';
    END IF;
END
$$;

DELETE FROM performer_prize
WHERE "prizeId" IN (SELECT id FROM owned_prizes)
  AND "performerId" IN (SELECT id FROM owned_performers);
DELETE FROM performer WHERE id IN (SELECT id FROM owned_performers);
DELETE FROM prize WHERE id IN (SELECT id FROM owned_prizes);
COMMIT;

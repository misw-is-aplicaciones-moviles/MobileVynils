-- La semilla de BackVynils inserta IDs explícitos, sin avanzar sus secuencias.
-- Preparación repetible: no fija IDs, no reduce contadores y no borra datos.
DO $$
DECLARE
    table_name text;
    sequence_name text;
    maximum_id bigint;
    sequence_value bigint;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['album', 'track'] LOOP
        EXECUTE format('LOCK TABLE public.%I IN SHARE ROW EXCLUSIVE MODE', table_name);
        sequence_name := pg_get_serial_sequence(format('public.%I', table_name), 'id');
        IF sequence_name IS NULL THEN
            RAISE EXCEPTION 'No existe la secuencia de public.%.id; revisar la versión de BackVynils', table_name;
        END IF;
        EXECUTE format('SELECT max(id) FROM public.%I', table_name) INTO maximum_id;
        EXECUTE format('SELECT last_value FROM %s', sequence_name) INTO sequence_value;
        IF maximum_id IS NOT NULL THEN
            PERFORM setval(sequence_name::regclass, GREATEST(maximum_id, sequence_value), true);
        END IF;
    END LOOP;
END
$$;

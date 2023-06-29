# MetroHero Server

## Setup

The setup instructions below are for Ubuntu 16.04. They may work for newer versions of Ubuntu or other Debian-based distros too, but some modifications of the commands provided may be required. YMMV.

1. Install Oracle Java 9 JDK:
    ```
    sudo add-apt-repository ppa:webupd8team/java
    sudo apt update
    sudo apt install oracle-java9-installer
    ```
   
2. Install Maven 3:
   ```
   sudo apt install maven
   ```

3. Install PostgreSQL 10:
    ```
    sudo add-apt-repository 'deb http://apt.postgresql.org/pub/repos/apt/ xenial-pgdg main'
    wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
    sudo apt update
    sudo apt install postgresql-10
    ```

4. Set `postgres` user's password to `postgres`:
    ```
    sudo su - postgres
    psql
    \password
    ```

5. Create a new, empty `metrohero` database:
    ```
    sudo su - postgres
    createdb metrohero
    ```

6. Create custom PostgreSQL routines:
    ```
    sudo su - postgres
    psql
    \c metrohero
    ```
    ```postgresql
    create function ts_round(timestamp with time zone, integer) returns timestamp with time zone
        language sql
    as $$
    SELECT 'epoch'::timestamptz + '1 second'::INTERVAL * ( $2 * ( extract( epoch FROM $1 )::INT4 / $2 ) );
    $$
    ;
    
    create function interval_to_seconds(interval) returns double precision
        language sql
    as $$
    SELECT (extract(days from $1) * 86400)
           + (extract(hours from $1) * 3600)
           + (extract(minutes from $1) * 60)
           + extract(seconds from $1);
    $$
    ;
    
    create function weighted_stddev_state(state numeric[], val numeric, weight numeric) returns numeric[]
        language plpgsql
    as $$
    BEGIN
      IF weight IS NULL OR val IS NULL
      THEN RETURN state;
      ELSE RETURN ARRAY[state[1]+weight, state[2]+val*weight, state[3]+val^2*weight];
      END IF;
    END;
    $$
    ;
    
    create function weighted_stddev_combiner(state numeric[], numeric, numeric) returns numeric
        language plpgsql
    as $$
    BEGIN
      RETURN sqrt((state[3]-(state[2]^2)/state[1])/(state[1]-1));
    END;
    $$
    ;
    
    CREATE OR REPLACE FUNCTION weighted_stddev_state(state numeric[], val numeric, weight numeric) RETURNS numeric[3] AS
    $$
    BEGIN
            IF weight IS NULL OR val IS NULL
            THEN RETURN state;
            ELSE RETURN ARRAY[state[1]+weight, state[2]+val*weight, state[3]+val^2*weight];
            END IF;
    END;
    $$
    LANGUAGE plpgsql;
    
    
    CREATE OR REPLACE FUNCTION weighted_stddev_combiner(state numeric[], numeric, numeric) RETURNS numeric AS
    $$
    BEGIN
            RETURN sqrt((state[3]-(state[2]^2)/state[1])/(state[1]-1));
    END;
    $$
    LANGUAGE plpgsql;
    
    
    CREATE AGGREGATE weighted_stddev(var numeric, weight numeric)
    (
            sfunc = weighted_stddev_state,
            stype = numeric[3],
            finalfunc = weighted_stddev_combiner,
            initcond = '{0,0,0}',
            finalfunc_extra
    );
    COMMENT ON AGGREGATE weighted_stddev(numeric, numeric) IS 'Usage: select weighted_stddev(var::numeric, weight::numeric) from X;';
    
    CREATE AGGREGATE array_accum (anyarray)
    (
        sfunc = array_cat,
        stype = anyarray,
        initcond = '{}'
    );

    CREATE EXTENSION btree_gist;

    -- https://stackoverflow.com/a/38066008/1072621
    CREATE OR REPLACE FUNCTION merge_train_departure_info(new_train_id CHARACTER VARYING, new_real_train_id CHARACTER VARYING, new_departure_station_name CHARACTER VARYING, new_departure_station_code CHARACTER VARYING, new_line_name CHARACTER VARYING, new_line_code CHARACTER VARYING, new_direction_name CHARACTER VARYING, new_direction_number INTEGER, new_scheduled_destination_station_name CHARACTER VARYING, new_scheduled_destination_station_code CHARACTER VARYING, new_observed_destination_station_name CHARACTER VARYING, new_observed_destination_station_code CHARACTER VARYING, new_observed_num_cars INTEGER, new_observed_departure_time TIMESTAMP WITHOUT TIME ZONE, new_scheduled_departure_time TIMESTAMP WITHOUT TIME ZONE, new_observed_time_since_last_departure NUMERIC, new_scheduled_time_since_last_departure NUMERIC, new_headway_deviation NUMERIC, new_schedule_deviation DOUBLE PRECISION) RETURNS BOOLEAN AS
    $$
    BEGIN
      LOOP
        -- first try to update an existing record
        BEGIN
          UPDATE train_departure_info
          SET
            train_id = new_train_id,
            real_train_id = new_real_train_id,
            departure_station_name = new_departure_station_name,
            line_name = new_line_name,
            direction_name = new_direction_name,
            scheduled_destination_station_name = new_scheduled_destination_station_name,
            scheduled_destination_station_code = new_scheduled_destination_station_code,
            observed_destination_station_name = new_observed_destination_station_name,
            observed_destination_station_code = new_observed_destination_station_code,
            observed_num_cars = new_observed_num_cars,
            observed_departure_time = new_observed_departure_time,
            scheduled_departure_time = new_scheduled_departure_time,
            observed_time_since_last_departure = new_observed_time_since_last_departure,
            scheduled_time_since_last_departure = new_scheduled_time_since_last_departure,
            headway_deviation = new_headway_deviation,
            schedule_deviation = new_schedule_deviation
          WHERE
            train_departure_info.departure_station_code = new_departure_station_code AND
            train_departure_info.line_code = new_line_code AND
            train_departure_info.direction_number = new_direction_number AND
            ((observed_departure_time IS NOT NULL AND scheduled_departure_time IS NULL AND observed_departure_time = new_observed_departure_time AND new_scheduled_departure_time IS NULL) OR
             (observed_departure_time IS NULL AND scheduled_departure_time IS NOT NULL AND new_observed_departure_time IS NULL AND scheduled_departure_time = new_scheduled_departure_time) OR
             (observed_departure_time IS NOT NULL AND scheduled_departure_time IS NOT NULL AND observed_departure_time = new_observed_departure_time AND scheduled_departure_time = new_scheduled_departure_time) OR
             (observed_departure_time IS NOT NULL AND scheduled_departure_time IS NULL AND observed_departure_time = new_observed_departure_time AND new_scheduled_departure_time IS NOT NULL) OR
             (observed_departure_time IS NULL AND scheduled_departure_time IS NOT NULL AND new_observed_departure_time IS NOT NULL AND scheduled_departure_time = new_scheduled_departure_time) OR
             (observed_departure_time IS NOT NULL AND scheduled_departure_time IS NOT NULL AND observed_departure_time != new_observed_departure_time AND scheduled_departure_time = new_scheduled_departure_time) OR
             (observed_departure_time IS NOT NULL AND scheduled_departure_time IS NOT NULL AND observed_departure_time = new_observed_departure_time AND scheduled_departure_time != new_scheduled_departure_time));
          IF found THEN
            -- update succeeded, so our job is done
            RETURN TRUE;
          END IF;
        EXCEPTION WHEN unique_violation THEN
          -- the update we requested would produce duplicate records
          -- delete the records we'd be updating and insert a new record instead by allowing the function to proceed
          RAISE WARNING 'Attempted to update multiple records to (%, %, %, %, %), which is not allowed as this would produce duplicate records. Deleting these records instead and inserting a new one. If some of these records should not be deleted but will be anyway because of this action, it is assumed their updated records will be inserted this tick or next tick.', new_departure_station_code, new_line_code, new_direction_number, new_observed_departure_time, new_scheduled_departure_time;
          DELETE FROM train_departure_info
          WHERE
              train_departure_info.departure_station_code = new_departure_station_code AND
              train_departure_info.line_code = new_line_code AND
              train_departure_info.direction_number = new_direction_number AND
              ((observed_departure_time IS NOT NULL AND scheduled_departure_time IS NULL AND observed_departure_time = new_observed_departure_time AND new_scheduled_departure_time IS NULL) OR
               (observed_departure_time IS NULL AND scheduled_departure_time IS NOT NULL AND new_observed_departure_time IS NULL AND scheduled_departure_time = new_scheduled_departure_time) OR
               (observed_departure_time IS NOT NULL AND scheduled_departure_time IS NOT NULL AND observed_departure_time = new_observed_departure_time AND scheduled_departure_time = new_scheduled_departure_time) OR
               (observed_departure_time IS NOT NULL AND scheduled_departure_time IS NULL AND observed_departure_time = new_observed_departure_time AND new_scheduled_departure_time IS NOT NULL) OR
               (observed_departure_time IS NULL AND scheduled_departure_time IS NOT NULL AND new_observed_departure_time IS NOT NULL AND scheduled_departure_time = new_scheduled_departure_time) OR
               (observed_departure_time IS NOT NULL AND scheduled_departure_time IS NOT NULL AND observed_departure_time != new_observed_departure_time AND scheduled_departure_time = new_scheduled_departure_time) OR
               (observed_departure_time IS NOT NULL AND scheduled_departure_time IS NOT NULL AND observed_departure_time = new_observed_departure_time AND scheduled_departure_time != new_scheduled_departure_time));
        END;
    
        -- no matching record to update, so insert a new one
        BEGIN
          INSERT INTO train_departure_info (
            train_id,
            real_train_id,
            departure_station_name,
            departure_station_code,
            line_name,
            line_code,
            direction_name,
            direction_number,
            scheduled_destination_station_name,
            scheduled_destination_station_code,
            observed_destination_station_name,
            observed_destination_station_code,
            observed_num_cars,
            observed_departure_time,
            scheduled_departure_time,
            observed_time_since_last_departure,
            scheduled_time_since_last_departure,
            headway_deviation,
            schedule_deviation
          ) VALUES (
            new_train_id,
            new_real_train_id,
            new_departure_station_name,
            new_departure_station_code,
            new_line_name,
            new_line_code,
            new_direction_name,
            new_direction_number,
            new_scheduled_destination_station_name,
            new_scheduled_destination_station_code,
            new_observed_destination_station_name,
            new_observed_destination_station_code,
            new_observed_num_cars,
            new_observed_departure_time,
            new_scheduled_departure_time,
            new_observed_time_since_last_departure,
            new_scheduled_time_since_last_departure,
            new_headway_deviation,
            new_schedule_deviation
          )
          ON CONFLICT (departure_station_code, line_code, direction_number, observed_departure_time) DO UPDATE
            SET
              train_id = EXCLUDED.train_id,
              real_train_id = EXCLUDED.real_train_id,
              departure_station_name = EXCLUDED.departure_station_name,
              departure_station_code = EXCLUDED.departure_station_code,
              line_name = EXCLUDED.line_name,
              line_code = EXCLUDED.line_code,
              direction_name = EXCLUDED.direction_name,
              direction_number = EXCLUDED.direction_number,
              scheduled_destination_station_name = EXCLUDED.scheduled_destination_station_name,
              scheduled_destination_station_code = EXCLUDED.scheduled_destination_station_code,
              observed_destination_station_name = EXCLUDED.observed_destination_station_name,
              observed_destination_station_code = EXCLUDED.observed_destination_station_code,
              observed_num_cars = EXCLUDED.observed_num_cars,
              observed_departure_time = EXCLUDED.observed_departure_time,
              scheduled_departure_time = EXCLUDED.scheduled_departure_time,
              observed_time_since_last_departure = EXCLUDED.observed_time_since_last_departure,
              scheduled_time_since_last_departure = EXCLUDED.scheduled_time_since_last_departure,
              headway_deviation = EXCLUDED.headway_deviation,
              schedule_deviation = EXCLUDED.schedule_deviation;
          -- all is well; record inserted
          RETURN TRUE;
        EXCEPTION WHEN unique_violation THEN
          -- insert failed due to another constraint, probably the scheduled_departure_time one
          -- try again with an upsert against that particular constraint
          BEGIN
            INSERT INTO train_departure_info (
              train_id,
              real_train_id,
              departure_station_name,
              departure_station_code,
              line_name,
              line_code,
              direction_name,
              direction_number,
              scheduled_destination_station_name,
              scheduled_destination_station_code,
              observed_destination_station_name,
              observed_destination_station_code,
              observed_num_cars,
              observed_departure_time,
              scheduled_departure_time,
              observed_time_since_last_departure,
              scheduled_time_since_last_departure,
              headway_deviation,
              schedule_deviation
            ) VALUES (
              new_train_id,
              new_real_train_id,
              new_departure_station_name,
              new_departure_station_code,
              new_line_name,
              new_line_code,
              new_direction_name,
              new_direction_number,
              new_scheduled_destination_station_name,
              new_scheduled_destination_station_code,
              new_observed_destination_station_name,
              new_observed_destination_station_code,
              new_observed_num_cars,
              new_observed_departure_time,
              new_scheduled_departure_time,
              new_observed_time_since_last_departure,
              new_scheduled_time_since_last_departure,
              new_headway_deviation,
              new_schedule_deviation
            )
            ON CONFLICT (departure_station_code, line_code, direction_number, scheduled_departure_time) DO UPDATE
              SET
                train_id = EXCLUDED.train_id,
                real_train_id = EXCLUDED.real_train_id,
                departure_station_name = EXCLUDED.departure_station_name,
                departure_station_code = EXCLUDED.departure_station_code,
                line_name = EXCLUDED.line_name,
                line_code = EXCLUDED.line_code,
                direction_name = EXCLUDED.direction_name,
                direction_number = EXCLUDED.direction_number,
                scheduled_destination_station_name = EXCLUDED.scheduled_destination_station_name,
                scheduled_destination_station_code = EXCLUDED.scheduled_destination_station_code,
                observed_destination_station_name = EXCLUDED.observed_destination_station_name,
                observed_destination_station_code = EXCLUDED.observed_destination_station_code,
                observed_num_cars = EXCLUDED.observed_num_cars,
                observed_departure_time = EXCLUDED.observed_departure_time,
                scheduled_departure_time = EXCLUDED.scheduled_departure_time,
                observed_time_since_last_departure = EXCLUDED.observed_time_since_last_departure,
                scheduled_time_since_last_departure = EXCLUDED.scheduled_time_since_last_departure,
                headway_deviation = EXCLUDED.headway_deviation,
                schedule_deviation = EXCLUDED.schedule_deviation;
            -- all is well (finally); record inserted
            RETURN TRUE;
          EXCEPTION WHEN unique_violation THEN
            -- another constraint (probably the first observed_departure_time one we checked) is now the problem
            -- do nothing; next iteration of the loop will try that again (after trying to update again first)
            RAISE WARNING 'failed to insert (%, %, %, %, %)', new_departure_station_code, new_line_code, new_direction_number, new_observed_departure_time, new_scheduled_departure_time;
          END;
        END;
      END LOOP;
    END;
    $$
    LANGUAGE plpgsql;
    ```

7. Run the Spring Boot application targeting the `Application` Java class. This will populate the `metrohero` database. Once the application is running, stop it and continue to the next step to continue setup. See the Usage section of this README if you need help starting the server.
8. Populate the `station_to_station_travel_time` table in the `metrohero` database
    ```postgresql
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K02_K01', 2086, 'K02', '2016-11-15 08:00:36.595000', 'K01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F04_F03', 3607, 'F04', '2016-12-02 10:14:36.255000', 'F03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F02_F03', 2351, 'F02', '2016-12-07 22:00:38.143000', 'F03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E04_E05', 4139, 'E04', '2016-12-16 13:02:40.266000', 'E05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E10_E09', 12678, 'E10', '2017-03-20 17:14:07.099000', 'E09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D13_D12', 7051, 'D13', '2017-02-11 16:20:54.213000', 'D12') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F11_F10', 8550, 'F11', '2017-04-20 10:54:50.414000', 'F10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('J03_J02', 18092, 'J03', '2017-01-21 00:07:56.644000', 'J02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A12_A13', 5295, 'A12', '2016-12-28 11:38:01.094000', 'A13') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E07_E06', 9808, 'E07', '2017-01-05 09:24:44.252000', 'E06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B06_B07', 9435, 'B06', '2017-01-05 09:25:02.907000', 'B07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C05_C04', 6458, 'C05', '2017-01-05 09:25:15.138000', 'C04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C15_C14', 2189, 'C15', '2017-01-29 16:56:37.331000', 'C14') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K03_K04', 2374, 'K03', '2017-01-11 14:49:47.504000', 'K04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C14_C13', 3125, 'C14', '2016-11-21 13:22:12.246000', 'C13') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A04_A05', 3140, 'A04', '2016-11-16 12:25:06.668000', 'A05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A02_A03', 2105, 'A02', '2016-11-17 00:21:40.166000', 'A03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A01_A02', 3535, 'A01', '2016-11-23 19:00:56.115000', 'A02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A05_A04', 3140, 'A05', '2016-11-15 20:29:11.718000', 'A04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A02_A01', 3535, 'A02', '2016-11-03 15:33:23.635000', 'A01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A07_A06', 5240, 'A07', '2016-11-03 17:06:34.132000', 'A06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A08_A07', 3515, 'A08', '2016-11-03 10:01:04.275000', 'A07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B02_B01', 1365, 'B02', '2016-11-03 16:00:29.337000', 'B01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C05_C06', 4354, 'C05', '2016-11-21 08:06:28.468000', 'C06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C04_C03', 2166, 'C04', '2016-11-03 16:23:41.193000', 'C03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C05_K01', 5148, 'C05', '2016-11-03 16:31:19.368000', 'K01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C07_C08', 2637, 'C07', '2016-11-03 16:37:06.639000', 'C08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C08_C07', 2637, 'C08', '2016-11-03 16:30:48.811000', 'C07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C13_C14', 3125, 'C13', '2016-11-03 16:13:26.879000', 'C14') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D02_D01', 1416, 'D02', '2016-11-07 15:00:24.801000', 'D01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D02_D03', 2044, 'D02', '2016-11-03 15:08:00.787000', 'D03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D05_D04', 2452, 'D05', '2016-11-03 16:23:47.410000', 'D04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E01_E02', 1927, 'E01', '2016-11-03 15:09:57.787000', 'E02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E01_F01', 2385, 'E01', '2016-11-03 13:31:05.125000', 'F01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E07_E08', 6075, 'E07', '2016-11-03 16:31:07.740000', 'E08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F01_E01', 2385, 'F01', '2016-11-03 13:31:05.125000', 'E01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C07_F03', 11612, 'C07', '2016-11-22 21:03:26.446000', 'F03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A10_A09', 4923, 'A10', '2017-04-21 08:41:25.127000', 'A09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D09_D10', 4063, 'D09', '2017-01-18 15:37:03.807000', 'D10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A11_A10', 11242, 'A11', '2017-03-07 08:15:11.379000', 'A10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A14_A13', 9985, 'A14', '2017-05-08 18:20:38.942000', 'A13') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E03_E04', 4115, 'E03', '2016-11-12 19:01:02.004000', 'E04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C08_C09', 3468, 'C08', '2016-11-13 15:00:47.532000', 'C09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B01_A01', 905, 'B01', '2016-11-13 18:00:18.571000', 'A01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D04_D03', 1156, 'D04', '2016-12-08 06:00:24.946000', 'D03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E09_E08', 9432, 'E09', '2016-11-18 21:02:10.952000', 'E08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B05_B06', 6503, 'B05', '2017-07-05 18:27:52.746000', 'B06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A10_A11', 11242, 'A10', '2017-08-27 00:58:19.996000', 'A11') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C07_C06', 6404, 'C07', '2017-06-05 07:15:41.355000', 'C06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K01_K02', 2086, 'K01', '2017-09-23 00:04:39.486000', 'K02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E08_E07', 6075, 'E08', '2017-08-10 18:47:22.631000', 'E07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B11_B10', 8730, 'B11', '2017-06-15 22:55:19.235000', 'B10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C14_C15', 2189, 'C14', '2017-09-17 07:58:06.470000', 'C15') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D12_D13', 7051, 'D12', '2017-09-08 15:58:03.959000', 'D13') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D01_D02', 1416, 'D01', '2017-06-22 14:42:34.821000', 'D02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A08_A09', 8492, 'A08', '2017-09-12 07:42:33.164000', 'A09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A04_A03', 5660, 'A04', '2017-07-04 10:21:06.318000', 'A03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A14_A15', 13555, 'A14', '2017-07-20 18:33:55.310000', 'A15') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A06_A05', 2720, 'A06', '2017-08-14 10:55:25.808000', 'A05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F07_F06', 6236, 'F07', '2017-08-22 08:09:02.769000', 'F06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D11_D12', 9070, 'D11', '2017-08-03 19:28:56.288000', 'D12') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A03_A04', 5660, 'A03', '2017-08-24 16:16:54.658000', 'A04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A12_A11', 6673, 'A12', '2017-09-20 16:26:50.844000', 'A11') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C03_C02', 1401, 'C03', '2017-08-20 22:23:47.535000', 'C02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C09_C08', 3468, 'C09', '2017-08-24 09:40:24.918000', 'C08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D01_C01', 961, 'D01', '2017-08-21 15:16:43.683000', 'C01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A01_B01', 905, 'A01', '2017-08-28 07:01:05.146000', 'B01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A09_A08', 8492, 'A09', '2017-09-01 23:19:02.507000', 'A08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D08_G01', 12795, 'D08', '2017-08-31 15:26:37.365000', 'G01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B10_B11', 8730, 'B10', '2017-08-20 20:01:58.316000', 'B11') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E06_E05', 7981, 'E06', '2017-09-05 07:55:16.797000', 'E05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('G04_G05', 6600, 'G04', '2017-09-12 06:57:41.075000', 'G05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N01_N02', 2840, 'N01', '2017-08-23 12:22:03.651000', 'N02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F05_F06', 5575, 'F05', '2017-09-12 05:44:52.404000', 'F06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E08_E09', 9432, 'E08', '2017-08-17 17:33:40.607000', 'E09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D08_D09', 10424, 'D08', '2017-08-28 06:36:57.835000', 'D09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F01_F02', 1279, 'F01', '2017-09-12 07:00:20.500000', 'F02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B02_B03', 2838, 'B02', '2017-09-17 20:55:25.225000', 'B03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B09_B08', 8332, 'B09', '2017-09-15 18:19:28.955000', 'B08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('J02_J03', 18092, 'J02', '2017-09-18 17:02:15.560000', 'J03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B10_B09', 7950, 'B10', '2017-09-19 18:25:36.035000', 'B09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C02_C03', 1401, 'C02', '2017-08-16 18:42:14.502000', 'C03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K07_K08', 12564, 'K07', '2017-08-16 22:28:37.991000', 'K08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D08_D07', 3150, 'D08', '2017-08-30 19:20:02.587000', 'D07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E09_E10', 12678, 'E09', '2017-09-16 14:09:32.523000', 'E10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B35_B03', 2949, 'B35', '2017-08-20 14:55:52.306000', 'B03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D09_D08', 10424, 'D09', '2017-08-25 10:14:16.328000', 'D08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B03_B35', 2949, 'B03', '2017-08-30 19:11:07.842000', 'B35') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D06_D07', 2689, 'D06', '2017-08-24 12:28:26.179000', 'D07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A06_A07', 5240, 'A06', '2017-08-24 12:28:51.520000', 'A07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A09_A10', 4923, 'A09', '2017-08-24 12:28:57.779000', 'A10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C06_C07', 6404, 'C06', '2017-08-24 12:29:16.485000', 'C07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D03_D04', 1156, 'D03', '2017-08-24 13:47:26.770000', 'D04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E05_E06', 7981, 'E05', '2017-08-24 17:48:49.892000', 'E06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A03_A02', 2105, 'A03', '2017-09-09 16:47:52.530000', 'A02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C09_C10', 2509, 'C09', '2017-09-09 17:49:16.294000', 'C10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F02_F01', 1279, 'F02', '2017-08-29 18:16:38.460000', 'F01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A07_A08', 3515, 'A07', '2017-08-30 12:08:08.850000', 'A08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C03_C04', 2166, 'C03', '2017-09-02 12:48:14.468000', 'C04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E06_E07', 9808, 'E06', '2017-09-07 11:23:11.460000', 'E07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B01_B02', 1365, 'B01', '2017-09-13 07:56:54.342000', 'B02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D07_D08', 3150, 'D07', '2017-09-22 13:49:05.208000', 'D08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N04_N06', 30257, 'N04', '2017-09-26 20:29:57.858000', 'N06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E02_E01', 1927, 'E02', '2016-11-23 18:51:15.143000', 'E01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E03_E02', 1956, 'E03', '2016-12-05 07:00:32.333000', 'E02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D06_D05', 2103, 'D06', '2016-11-09 00:00:32.439000', 'D05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C13_C12', 2845, 'C13', '2016-12-07 11:00:37.661000', 'C12') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C06_C05', 4354, 'C06', '2016-11-17 19:01:05.377000', 'C05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F03_F02', 2351, 'F03', '2016-11-03 16:23:47.410000', 'F02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F03_F04', 3607, 'F03', '2016-11-03 14:01:00.732000', 'F04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F04_F05', 2726, 'F04', '2016-11-03 16:30:42.405000', 'F05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F06_F05', 5575, 'F06', '2016-11-03 16:31:29.631000', 'F05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F07_F08', 4945, 'F07', '2016-11-03 13:31:51.270000', 'F08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K06_K05', 10339, 'K06', '2016-11-03 10:02:22.038000', 'K05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N04_N03', 3034, 'N04', '2016-11-03 16:18:46.065000', 'N03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('G05_G04', 6600, 'G05', '2016-11-10 17:51:48.229000', 'G04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K01_C05', 5148, 'K01', '2016-11-23 11:01:23.621000', 'C05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F03_C07', 11612, 'F03', '2016-11-11 22:03:34.308000', 'C07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K03_K02', 1872, 'K03', '2016-11-14 00:00:33.572000', 'K02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K02_K03', 1872, 'K02', '2016-11-29 06:00:28.910000', 'K03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F06_F07', 6236, 'F06', '2016-11-10 16:01:14.354000', 'F07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N02_N03', 3302, 'N02', '2016-11-30 13:00:56.563000', 'N03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B08_B09', 8332, 'B08', '2017-01-05 09:44:05.092000', 'B09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C01_C02', 1825, 'C01', '2016-12-13 14:47:02.983000', 'C02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B35_B04', 5163, 'B35', '2017-02-27 08:01:24.424000', 'B04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('G04_G03', 7565, 'G04', '2017-05-04 00:19:36.849000', 'G03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F08_F09', 6000, 'F08', '2017-01-25 11:28:52.783000', 'F09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('G03_G02', 4615, 'G03', '2017-04-12 18:30:23.435000', 'G02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A11_A12', 6673, 'A11', '2017-03-08 11:28:52.486000', 'A12') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B04_B35', 5163, 'B04', '2017-04-02 14:40:19.722000', 'B35') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C10_C09', 2509, 'C10', '2017-02-14 19:25:29.931000', 'C09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A13_A14', 9985, 'A13', '2017-02-18 06:52:14.382000', 'A14') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A13_A12', 5295, 'A13', '2017-03-11 08:06:48.134000', 'A12') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('G01_D08', 12795, 'G01', '2017-04-25 08:51:58.801000', 'D08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B09_B10', 7950, 'B09', '2017-09-07 18:20:35.327000', 'B10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K06_K07', 12081, 'K06', '2017-09-25 18:55:57.898000', 'K07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D10_D11', 5196, 'D10', '2017-08-17 06:38:55.764000', 'D11') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A15_A14', 13555, 'A15', '2017-05-22 22:18:52.212000', 'A14') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B07_B06', 9435, 'B07', '2017-08-30 23:18:01.277000', 'B06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B05_B04', 3953, 'B05', '2017-06-01 19:50:40.573000', 'B04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K05_K06', 10339, 'K05', '2017-09-14 16:01:01.040000', 'K06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K08_K07', 12564, 'K08', '2017-06-06 13:33:04.311000', 'K07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C04_C05', 6458, 'C04', '2017-06-07 12:10:06.737000', 'C05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C01_D01', 961, 'C01', '2017-08-31 16:50:35.578000', 'D01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F09_F10', 7035, 'F09', '2017-06-16 19:28:34.739000', 'F10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C10_C12', 15483, 'C10', '2017-06-16 19:28:41.066000', 'C12') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N02_N01', 2840, 'N02', '2017-09-14 16:44:52.019000', 'N01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('G02_G03', 4615, 'G02', '2017-06-23 10:17:32.819000', 'G03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D12_D11', 9070, 'D12', '2017-08-12 21:43:03.087000', 'D11') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N06_N04', 30257, 'N06', '2017-06-30 22:01:58.630000', 'N04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C13_J02', 19765, 'C13', '2017-09-14 16:59:13.858000', 'J02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B08_B07', 6889, 'B08', '2017-08-25 17:51:05.482000', 'B07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D04_D05', 2452, 'D04', '2017-07-15 23:46:55.251000', 'D05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D10_D09', 4063, 'D10', '2017-07-21 17:55:37.296000', 'D09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D11_D10', 5196, 'D11', '2017-07-27 17:21:03.652000', 'D10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('G02_G01', 7179, 'G02', '2017-08-31 17:08:37.744000', 'G01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E04_E03', 4115, 'E04', '2017-08-17 14:15:14.749000', 'E03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B03_B02', 2838, 'B03', '2017-08-31 22:46:04.338000', 'B02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F10_F11', 8550, 'F10', '2017-09-09 01:14:39.954000', 'F11') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('G01_G02', 7179, 'G01', '2017-08-16 08:33:47.641000', 'G02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K07_K06', 12081, 'K07', '2017-08-23 16:23:33.158000', 'K06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F05_F04', 2726, 'F05', '2017-08-27 20:36:28.854000', 'F04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D03_D02', 2044, 'D03', '2017-09-09 21:20:33.681000', 'D02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F10_F09', 7035, 'F10', '2017-09-10 01:03:09.919000', 'F09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C12_C10', 15483, 'C12', '2017-09-16 10:07:13.434000', 'C10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F09_F08', 6000, 'F09', '2017-09-11 16:06:15.140000', 'F08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E02_E03', 1956, 'E02', '2017-08-16 14:52:07.525000', 'E03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('J02_C13', 19765, 'J02', '2017-08-13 17:23:02.631000', 'C13') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('A05_A06', 2720, 'A05', '2017-09-17 00:45:28.653000', 'A06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C02_C01', 1825, 'C02', '2017-08-28 08:17:45.776000', 'C01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B04_B05', 3953, 'B04', '2017-08-18 17:25:10.872000', 'B05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N01_K05', 24031, 'N01', '2017-09-12 15:27:15.659000', 'K05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D07_D06', 2689, 'D07', '2017-09-17 11:33:37.268000', 'D06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('F08_F07', 4945, 'F08', '2017-08-24 09:40:56.395000', 'F07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('E05_E04', 4139, 'E05', '2017-08-24 12:28:51.520000', 'E04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B06_B05', 6503, 'B06', '2017-08-24 12:29:16.485000', 'B05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K05_K04', 12549, 'K05', '2017-08-24 12:30:19.963000', 'K04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N03_N04', 3034, 'N03', '2017-09-18 20:25:53.062000', 'N04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K04_K05', 12549, 'K04', '2017-09-13 19:19:57.948000', 'K05') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K05_N01', 24031, 'K05', '2017-09-20 09:07:57.220000', 'N01') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('D05_D06', 2103, 'D05', '2017-08-29 10:33:30.255000', 'D06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N03_N02', 3302, 'N03', '2017-09-21 17:05:44.908000', 'N02') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('C12_C13', 2845, 'C12', '2017-09-05 06:08:49.855000', 'C13') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('G03_G04', 7565, 'G03', '2017-09-05 22:18:24.674000', 'G04') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('K04_K03', 2374, 'K04', '2017-09-13 20:12:43.346000', 'K03') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('B07_B08', 6889, 'B07', '2017-09-25 08:51:09.185000', 'B08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N06_N07', 7355, 'N06', '2022-11-15 20:21:00.000000', 'N07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N07_N08', 6176, 'N07', '2022-11-15 20:21:00.000000', 'N08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N08_N09', 8475, 'N08', '2022-11-15 20:21:00.000000', 'N09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N09_N10', 10397, 'N09', '2022-11-15 20:21:00.000000', 'N10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N10_N11', 14545, 'N10', '2022-11-15 20:21:00.000000', 'N11') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N11_N12', 9470, 'N11', '2022-11-15 20:21:00.000000', 'N12') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N12_N11', 9470, 'N12', '2022-11-15 20:21:00.000000', 'N11') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N11_N10', 14545, 'N11', '2022-11-15 20:21:00.000000', 'N10') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N10_N09', 10397, 'N10', '2022-11-15 20:21:00.000000', 'N09') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N09_N08', 8475, 'N09', '2022-11-15 20:21:00.000000', 'N08') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N08_N07', 6176, 'N08', '2022-11-15 20:21:00.000000', 'N07') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
   INSERT INTO public.station_to_station_travel_time (station_codes_key, distance, from_station_code, last_updated, to_station_code) VALUES ('N07_N06', 7355, 'N07', '2022-11-15 20:21:00.000000', 'N06') ON CONFLICT (station_codes_key) DO UPDATE SET distance = EXCLUDED.distance;
    ```

## Usage

1. Replace the values for the `wmata.production.apikey` and `wmata.development.apikey` properties in src/main/resources/application.properties with your own API keys from WMATA. If you're already logged into developer.wmata.com, [click here](https://developer.wmata.com/developer), then copy the value for your "Primary key" into `wmata.production.apikey` and your "Secondary key" into `wmata.development.apikey`. If you have not yet been issued API keys from WMATA, [start here](https://developer.wmata.com/signup).
2. If you want any of the features powered by Twitter to work, replace the values for the `oauth.consumerKey`, `oauth.consumerSecret`, `oauth.accessToken`, and `oauth.accessTokenSecret` properties in src/main/resources/twitter4j.properties with your own credentials from Twitter. If you're already logged into developer.twitter.com and already have already created a Standalone App, go to the 'Keys and tokens' section of that app to generate an access token and secret. If you have not yet created a Twitter Developer account, [start here](https://developer.twitter.com/en/portal/petition/essential/basic-info).
3. The server is configured for debug mode by default. You can control this with the `developmentmode` property in src/main/resources/application.properties. Stay in this mode during development, and toggle it off in your production environment.
4. You should probably replace the self-signed cert `metrohero.jks` located in the root project directory with an actual cert from an actual authority. The provided self-signed cert should be sufficient for development purposes if you ignore any SSL warnings from your browser when trying to actually connect to the server, but it is not appropriate to use in production. A website like [SSL for Free](https://www.sslforfree.com/) might be a good place to start.
5. If you're using IntelliJ or another fully-featured IDE, you can use the autoconfigured Spring configuration (targeting the `Application` Java class) to start the server for development purposes, otherwise you can use `sudo mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx16g"`, e.g. in a production environment.
6. If you're running the server locally, navigate to https://localhost:9443/ to start using the webapp with it connected to your server.
7. If the logs are a little too noisy for your use case, e.g. in production, consider setting the `logging.level.com.jamespizzurro.metrorailserver` property in src/main/resources/application.properties to WARN instead of INFO, or set it to DEBUG to get even more log output for debugging purposes.

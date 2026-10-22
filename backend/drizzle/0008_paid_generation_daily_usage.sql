CREATE TABLE "paid_generation_daily_usage" (
	"day" date PRIMARY KEY NOT NULL,
	"reserved_variants" integer NOT NULL,
	CONSTRAINT "paid_generation_daily_usage_nonnegative" CHECK ("paid_generation_daily_usage"."reserved_variants" >= 0)
);
--> statement-breakpoint
-- Existing ElevenLabs variant rows also reserve usage when this guard is first installed.
-- No account identifier is retained; future account deletion cannot refund these reservations.
INSERT INTO "paid_generation_daily_usage" ("day", "reserved_variants")
SELECT ("created_at" AT TIME ZONE 'UTC')::date, count(*)::integer
FROM "tracks"
WHERE "provider_name" = 'elevenlabs'
GROUP BY ("created_at" AT TIME ZONE 'UTC')::date;

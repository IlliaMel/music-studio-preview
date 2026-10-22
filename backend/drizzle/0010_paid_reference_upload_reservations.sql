-- Legacy upload attempts have no proven reservation or attempt identity.
ALTER TABLE "uploads" ADD COLUMN "provider_attempt_id" uuid;
--> statement-breakpoint
ALTER TABLE "uploads" ADD COLUMN "paid_reservation_day" text;

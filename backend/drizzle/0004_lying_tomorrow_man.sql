ALTER TABLE "tracks" ADD COLUMN "provider_name" text DEFAULT 'mock' NOT NULL;--> statement-breakpoint
ALTER TABLE "uploads" ADD COLUMN "provider_song_id" text;--> statement-breakpoint
ALTER TABLE "uploads" ADD COLUMN "provider_attempted" boolean DEFAULT false NOT NULL;--> statement-breakpoint
ALTER TABLE "uploads" ADD COLUMN "duration_ms" integer;
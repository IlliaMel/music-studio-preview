CREATE TYPE "public"."track_status" AS ENUM('queued', 'running', 'done', 'failed');--> statement-breakpoint
CREATE TABLE "tracks" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"idempotency_key" text NOT NULL,
	"status" "track_status" DEFAULT 'queued' NOT NULL,
	"prompt" text NOT NULL,
	"length_ms" integer,
	"instrumental" boolean NOT NULL,
	"model_id" text NOT NULL,
	"title" text,
	"duration_ms" integer,
	"genres" jsonb,
	"explicit" boolean,
	"words" jsonb,
	"song_id" text,
	"error_code" text,
	"error_message" text,
	"prompt_suggestion" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	"started_at" timestamp with time zone,
	"finished_at" timestamp with time zone
);
--> statement-breakpoint
ALTER TABLE "tracks" ADD CONSTRAINT "tracks_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "tracks_user_idempotency_key" ON "tracks" USING btree ("user_id","idempotency_key");--> statement-breakpoint
CREATE INDEX "tracks_user_created_at" ON "tracks" USING btree ("user_id","created_at","id");--> statement-breakpoint
CREATE INDEX "tracks_queued_created_at" ON "tracks" USING btree ("created_at") WHERE "tracks"."status" = 'queued';
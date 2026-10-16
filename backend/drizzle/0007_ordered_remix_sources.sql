ALTER TABLE "tracks" ADD COLUMN "remix_track_ids" jsonb DEFAULT '[]'::jsonb NOT NULL;
--> statement-breakpoint
UPDATE "tracks" SET "remix_track_ids" = jsonb_build_array("reference_track_id") WHERE "reference_track_id" IS NOT NULL;

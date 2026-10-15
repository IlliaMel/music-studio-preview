CREATE TABLE "likes" (
	"user_id" uuid NOT NULL,
	"track_id" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "likes_user_id_track_id_pk" PRIMARY KEY("user_id","track_id")
);

--> statement-breakpoint
CREATE TABLE "playlist_tracks" (
	"playlist_id" uuid NOT NULL,
	"track_id" text NOT NULL,
	"added_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "playlist_tracks_playlist_id_track_id_pk" PRIMARY KEY("playlist_id","track_id")
);

--> statement-breakpoint
CREATE TABLE "playlists" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"name" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);

--> statement-breakpoint
CREATE TABLE "uploads" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"content_type" text NOT NULL,
	"size_bytes" integer NOT NULL,
	"data" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);

--> statement-breakpoint
ALTER TABLE "tracks" ADD COLUMN "generation_id" uuid DEFAULT gen_random_uuid() NOT NULL;
--> statement-breakpoint
ALTER TABLE "tracks" ADD COLUMN "variant_index" integer DEFAULT 0 NOT NULL;
--> statement-breakpoint
ALTER TABLE "tracks" ADD COLUMN "variants" integer DEFAULT 1 NOT NULL;
--> statement-breakpoint
ALTER TABLE "tracks" ADD COLUMN "custom_lyrics" text;
--> statement-breakpoint
ALTER TABLE "tracks" ADD COLUMN "reference_upload_id" uuid;
--> statement-breakpoint
ALTER TABLE "tracks" ADD COLUMN "reference_track_id" text;
--> statement-breakpoint
ALTER TABLE "tracks" ADD COLUMN "quality" text DEFAULT 'auto' NOT NULL;
--> statement-breakpoint
ALTER TABLE "likes" ADD CONSTRAINT "likes_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
--> statement-breakpoint
ALTER TABLE "playlist_tracks" ADD CONSTRAINT "playlist_tracks_playlist_id_playlists_id_fk" FOREIGN KEY ("playlist_id") REFERENCES "public"."playlists"("id") ON DELETE cascade ON UPDATE no action;
--> statement-breakpoint
ALTER TABLE "playlists" ADD CONSTRAINT "playlists_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
--> statement-breakpoint
ALTER TABLE "uploads" ADD CONSTRAINT "uploads_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;

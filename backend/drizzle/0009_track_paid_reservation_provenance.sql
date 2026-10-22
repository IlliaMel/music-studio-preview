-- Legacy rows remain null: provider_name alone does not prove that paid admission reserved
-- a unit for that variant. Guessing would let an old unpaid row refund another paid request.
ALTER TABLE "tracks" ADD COLUMN "paid_reservation_day" text;

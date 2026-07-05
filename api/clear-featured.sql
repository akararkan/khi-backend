BEGIN;

UPDATE public.news
SET featured = FALSE,
    featured_order = NULL;

UPDATE public.projects
SET featured = FALSE,
    featured_order = NULL;

UPDATE public.writings
SET featured = FALSE,
    featured_order = NULL;

UPDATE public.videos
SET featured = FALSE,
    featured_order = NULL;

UPDATE public.sound_tracks
SET featured = FALSE,
    featured_order = NULL;

UPDATE public.image_collections
SET featured = FALSE,
    featured_order = NULL;

DELETE FROM public.featured_items;

COMMIT;

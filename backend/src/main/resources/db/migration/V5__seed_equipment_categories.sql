INSERT IGNORE INTO equipment_categories (id, parent_category_id, slug, name, description, is_active)
VALUES
    ('00000000-0000-4000-8000-000000000001', NULL, 'small-construction', 'Small Construction Equipment',
     'Equipment for small and medium construction projects.', TRUE);

INSERT IGNORE INTO equipment_categories (id, parent_category_id, slug, name, description, is_active)
VALUES
    ('00000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000001', 'demolition-hammers',
     'Demolition Hammers & Breakers', 'Electric and pneumatic demolition hammers.', TRUE),
    ('00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000001', 'core-cutters',
     'Diamond Core Cutters', 'Core drilling machines and accessories.', TRUE),
    ('00000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000001', 'plate-compactors',
     'Plate Compactors', 'Compactors for soil and paving work.', TRUE),
    ('00000000-0000-4000-8000-000000000005', '00000000-0000-4000-8000-000000000001', 'portable-generators',
     'Portable Generators', 'Portable power generators for job sites.', TRUE),
    ('00000000-0000-4000-8000-000000000006', '00000000-0000-4000-8000-000000000001', 'concrete-vibrators',
     'Concrete Needle Vibrators', 'Vibrators for concrete consolidation.', TRUE);

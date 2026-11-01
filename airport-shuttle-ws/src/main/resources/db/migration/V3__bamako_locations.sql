UPDATE locations SET name='Aéroport International Président Modibo Keïta-Sénou (BKO)',detail='Zone Arrivées, point de prise en charge principal',lat=12.5378,lng=-7.9431,airport=true WHERE id=1;
UPDATE locations SET name='Aéroport Modibo Keïta-Sénou - Départs',detail='Terminal principal, zone Départs',lat=12.5369,lng=-7.9424,airport=true WHERE id=2;
UPDATE locations SET name='Aéroport Modibo Keïta-Sénou - Parking',detail='Parking voyageurs, rendez-vous navette',lat=12.5390,lng=-7.9440,airport=true WHERE id=3;
UPDATE locations SET name='Centre-ville de Bamako',detail='Place de la Liberté, point de rendez-vous',lat=12.6392,lng=-8.0029,airport=false WHERE id=4;
UPDATE locations SET name='ACI 2000',detail='Boulevard du 22 Octobre 1946, Bamako',lat=12.6236,lng=-8.0311,airport=false WHERE id=5;
UPDATE locations SET name='Grand Marché de Bamako',detail='Quartier de Bagadadji, entrée principale',lat=12.6465,lng=-8.0017,airport=false WHERE id=6;
UPDATE locations SET name='Palais des Congrès de Bamako',detail='Hamdallaye ACI 2000, avenue de la Nation',lat=12.6269,lng=-8.0354,airport=false WHERE id=7;
INSERT INTO locations(name,detail,lat,lng,airport) VALUES
 ('Radisson Collection Hotel Bamako','Hamdallaye ACI 2000, Bamako',12.6231,-8.0370,false),
 ('Azalaï Hotel Bamako','Avenue du Mali, ACI 2000',12.6197,-8.0314,false),
 ('Hotel Salam','Boulevard du 22 Octobre 1946, Bamako',12.6403,-8.0127,false),
 ('Laico Hotel El Farouk Bamako','Boulevard du 22 Octobre 1946, Bamako',12.6400,-8.0113,false),
 ('Musée National du Mali','Colline de Koulouba, Bamako',12.6246,-8.0116,false);

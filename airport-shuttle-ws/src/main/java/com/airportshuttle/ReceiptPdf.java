package com.airportshuttle;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ReceiptPdf {
	private ReceiptPdf() {}

	public static byte[] create(TripService.Trip trip, boolean french) {
		String currency = String.format(french ? "% ,d" : "%,d", trip.fareXof()).replace(" ", "").trim() + " Francs CFA (XOF)";
		String usd = String.format(java.util.Locale.ROOT, "USD %.2f", trip.fareCents() / 100d);
		String distance = trip.distanceKm() == null ? (french ? "Non enregistree" : "Not recorded")
				: String.format(java.util.Locale.ROOT, "%.2f km", trip.distanceKm());
		String rate = String.format(java.util.Locale.ROOT, "1 USD = %.4f XOF", trip.usdToXof());
		String pickupTime = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm 'UTC'", java.util.Locale.US)
				.withZone(ZoneId.of("UTC")).format(trip.pickupAt());
		List<String> lines = new ArrayList<>();
		lines.add(french ? "Recu de trajet AS-" + trip.id() : "Trip receipt AS-" + trip.id());
		lines.add("");
		lines.add((french ? "Passager: " : "Passenger: ") + trip.passengerName());
		lines.add((french ? "Date du trajet: " : "Trip date: ") + pickupTime);
		lines.add((french ? "Prise en charge: " : "Pickup: ") + trip.pickup().name());
		lines.add((french ? "Destination: " : "Destination: ") + trip.destination().name());
		lines.add((french ? "Vol: " : "Flight: ") + trip.flight());
		lines.add((french ? "Passagers / bagages: " : "Passengers / bags: ") + trip.passengers() + " / " + trip.bags());
		lines.add("");
		lines.add((french ? "Distance estimee: " : "Estimated distance: ") + distance);
		if (trip.distanceKm() == null) lines.add(french ? "Distance d'origine non enregistree" : "Original distance not recorded");
		else lines.add(french ? "Tarif: 1 USD par kilometre" : "Rate: USD 1 per kilometer");
		lines.add((french ? "Conversion appliquee: " : "Applied conversion: ") + rate);
		lines.add((french ? "Tarif en dollars: " : "Fare in USD: ") + usd);
		lines.add((french ? "Total estime: " : "Estimated total: ") + currency);
		lines.add("");
		lines.add(french ? "Releve de trajet uniquement. Aucun paiement n'a ete encaisse par ce systeme."
				: "Trip record only. Payment has not been collected by this system.");
		lines.add("Rates by ExchangeRate-API.com");
		return render(lines, french ? "RECU DE TRAJET" : "TRIP RECEIPT");
	}

	private static byte[] render(List<String> lines, String title) {
		StringBuilder stream = new StringBuilder();
		stream.append("q 0.04 0.40 0.34 rg 0 736 612 56 re f Q\nBT 1 1 1 rg /F1 20 Tf 48 755 Td (").append(escape(title)).append(") Tj ET\n");
		int y = 704;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.isEmpty()) { y -= 12; continue; }
			String safe = escape(line);
			stream.append("BT /F1 ").append(i == 0 ? "16" : (line.startsWith("Estimated total:") || line.startsWith("Total estime:") ? "13" : "11"))
					.append(" Tf 48 ").append(y).append(" Td (").append(safe).append(") Tj ET\n");
			y -= 23;
		}
		stream.append("BT /F1 9 Tf 48 32 Td (AIRPORT SHUTTLE) Tj ET\n");
		byte[] content = stream.toString().getBytes(StandardCharsets.US_ASCII);
		List<byte[]> objects = List.of(
			bytes("<< /Type /Catalog /Pages 2 0 R >>"),
			bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
			bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>"),
			concat(bytes("<< /Length " + content.length + " >>\nstream\n"), content, bytes("\nendstream")),
			bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));
		ByteArrayOutputStream pdf = new ByteArrayOutputStream();
		write(pdf, bytes("%PDF-1.4\n%AirportShuttle\n"));
		List<Integer> offsets = new ArrayList<>(); offsets.add(0);
		for (int i = 0; i < objects.size(); i++) {
			offsets.add(pdf.size());
			write(pdf, bytes((i + 1) + " 0 obj\n")); write(pdf, objects.get(i)); write(pdf, bytes("\nendobj\n"));
		}
		int xref = pdf.size();
		write(pdf, bytes("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
		for (int i = 1; i < offsets.size(); i++) write(pdf, bytes(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offsets.get(i))));
		write(pdf, bytes("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF"));
		return pdf.toByteArray();
	}

	private static String escape(String value) {
		String ascii = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
				.replaceAll("[^\\x20-\\x7E]", "?");
		return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
	}
	private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
	private static byte[] concat(byte[] a, byte[] b, byte[] c) { byte[] all = new byte[a.length + b.length + c.length]; System.arraycopy(a,0,all,0,a.length);System.arraycopy(b,0,all,a.length,b.length);System.arraycopy(c,0,all,a.length+b.length,c.length);return all; }
	private static void write(ByteArrayOutputStream out, byte[] bytes) { out.write(bytes, 0, bytes.length); }
}

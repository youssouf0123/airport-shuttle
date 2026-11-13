package com.airportshuttle;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public final class ProfilePdf {
	private ProfilePdf() {}

	public static byte[] create(String title, List<String> rows) {
		List<String> lines = new ArrayList<>();
		for (String row : rows) {
			String value = ascii(row == null ? "" : row);
			while (value.length() > 78) {
				int breakAt = value.lastIndexOf(' ', 78);
				if (breakAt < 20) breakAt = 78;
				lines.add(value.substring(0, breakAt));
				value = "  " + value.substring(breakAt).stripLeading();
			}
			lines.add(value);
		}
		StringBuilder stream = new StringBuilder();
		stream.append("q 0.04 0.40 0.34 rg 0 736 612 56 re f Q\nBT 1 1 1 rg /F1 20 Tf 48 755 Td (").append(escape(title)).append(") Tj ET\n");
		int y = 706;
		for (String line : lines) {
			stream.append("BT /F1 11 Tf 48 ").append(y).append(" Td (").append(escape(line)).append(") Tj ET\n");
			y -= 25;
		}
		stream.append("BT /F1 9 Tf 48 32 Td (AIRPORT SHUTTLE - DISPATCH RECORD) Tj ET\n");
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
			offsets.add(pdf.size()); write(pdf, bytes((i + 1) + " 0 obj\n")); write(pdf, objects.get(i)); write(pdf, bytes("\nendobj\n"));
		}
		int xref = pdf.size(); write(pdf, bytes("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
		for (int i = 1; i < offsets.size(); i++) write(pdf, bytes(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offsets.get(i))));
		write(pdf, bytes("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF"));
		return pdf.toByteArray();
	}

	private static String ascii(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replaceAll("[^\\x20-\\x7E]", "?");
	}
	private static String escape(String value) { return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"); }
	private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
	private static byte[] concat(byte[] a, byte[] b, byte[] c) { byte[] all = new byte[a.length + b.length + c.length]; System.arraycopy(a,0,all,0,a.length);System.arraycopy(b,0,all,a.length,b.length);System.arraycopy(c,0,all,a.length+b.length,c.length);return all; }
	private static void write(ByteArrayOutputStream out, byte[] bytes) { out.write(bytes, 0, bytes.length); }
}

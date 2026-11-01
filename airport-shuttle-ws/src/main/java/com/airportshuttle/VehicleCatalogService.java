package com.airportshuttle;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Service
public class VehicleCatalogService {
 private static final String RESOURCE="vehicle-reference/vehicles.xml.gz";
 private List<VehicleRecord> vehicles=Collections.emptyList();

 @PostConstruct
 public void load(){
  List<VehicleRecord> loaded=new ArrayList<>();
  ClassPathResource resource=new ClassPathResource(RESOURCE);
  if(!resource.exists()){vehicles=loaded;return;}
  XMLInputFactory factory=XMLInputFactory.newFactory();
  try(InputStream input=new GZIPInputStream(resource.getInputStream())){
   XMLStreamReader reader=factory.createXMLStreamReader(input);VehicleRecord current=null;String element="";StringBuilder text=new StringBuilder();
   while(reader.hasNext()){
    int event=reader.next();
    if(event==XMLStreamConstants.START_ELEMENT){element=reader.getLocalName();text.setLength(0);if("vehicle".equals(element))current=new VehicleRecord();continue;}
    if(event==XMLStreamConstants.CHARACTERS&&current!=null&&StringUtils.hasText(element)){text.append(reader.getText());continue;}
    if(event==XMLStreamConstants.END_ELEMENT){String name=reader.getLocalName();if(current!=null&&StringUtils.hasText(element)&&name.equals(element))assign(current,element,text.toString());if("vehicle".equals(name)&&current!=null&&current.usable()){loaded.add(current);current=null;}element="";text.setLength(0);}
   }
   reader.close();vehicles=loaded;
  }catch(Exception ignored){vehicles=Collections.emptyList();}
 }
 public List<Integer> years(){return vehicles.stream().map(v->v.year).filter(y->y!=null&&y>=1990).distinct().sorted(Collections.reverseOrder()).toList();}
 public List<String> makes(Integer year){return vehicles.stream().filter(v->matchYear(v,year)).map(v->v.make).filter(StringUtils::hasText).map(this::normalize).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();}
 public List<String> models(Integer year,String make){return vehicles.stream().filter(v->matchYear(v,year)&&equals(v.make,make)).map(v->v.model).filter(StringUtils::hasText).map(this::normalize).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();}
 public List<String> engines(Integer year,String make,String model){Set<String> result=new LinkedHashSet<>();vehicles.stream().filter(v->matchYear(v,year)&&equals(v.make,make)&&equals(v.model,model)).forEach(v->{String full=java.util.stream.Stream.of(v.transmission,v.cylinders==null?"":v.cylinders+" cyl",v.displacement==null?"":v.displacement+" L",v.drive,v.fuelType).filter(StringUtils::hasText).collect(Collectors.joining(", "));if(StringUtils.hasText(full))result.add(normalize(full));});return result.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();}
 private void assign(VehicleRecord v,String name,String raw){String value=normalize(raw);if(!StringUtils.hasText(value))return;switch(name){case "year"->v.year=parse(value);case "make"->v.make=value;case "model"->v.model=value;case "cylinders"->v.cylinders=value;case "displ"->v.displacement=value;case "drive"->v.drive=value;case "trany"->v.transmission=value;case "fuelType1"->v.fuelType=value;default->{}}}
 private boolean matchYear(VehicleRecord v,Integer year){return year==null||(v.year!=null&&v.year.equals(year));}
 private boolean equals(String left,String right){return StringUtils.hasText(left)&&StringUtils.hasText(right)&&normalize(left).equalsIgnoreCase(normalize(right));}
 private Integer parse(String value){try{return Integer.valueOf(value);}catch(NumberFormatException ex){return null;}}
 private String normalize(String value){if(!StringUtils.hasText(value))return "";String trimmed=value.trim().replaceAll("\\s+"," ");if(trimmed.equals(trimmed.toUpperCase(Locale.US))){return java.util.Arrays.stream(trimmed.toLowerCase(Locale.US).split("\\s+")).map(word->word.isEmpty()?word:Character.toUpperCase(word.charAt(0))+word.substring(1)).collect(Collectors.joining(" "));}return trimmed;}
 private static final class VehicleRecord {Integer year;String make,model,cylinders,displacement,drive,transmission,fuelType;boolean usable(){return year!=null&&StringUtils.hasText(make)&&StringUtils.hasText(model);}}
}

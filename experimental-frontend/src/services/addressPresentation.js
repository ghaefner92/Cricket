export function addressTitle(place){
 return [place.street,place.houseNumber].filter(Boolean).join(' ')||place.label
}
export function addressPrecision(place,locale='en'){
 const de=locale==='de'
 if(place?.source==='geolocation')return de?'GPS-Standort':'GPS location'
 return place?.houseNumber?(de?'Mit Hausnummer':'House number confirmed'):(de?'Ungefähre Straße / Ort':'Approximate street / place')
}

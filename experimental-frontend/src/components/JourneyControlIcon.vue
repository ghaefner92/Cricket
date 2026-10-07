<script setup>
const props=defineProps({kind:{type:String,default:'search'}})
const paths={
 gps:'M7 0h2v3h3v1h1v3h3v2h-3v3h-1v1H9v3H7v-3H4v-1H3V9H0V7h3V4h1V3h3zM5 5v6h6V5zM7 7h2v2H7z',
 history:'M6 1h6v1h2v2h1v8h-1v2h-2v1H6v-1H4v-2H3v-2h2v2h1v1h6v-1h1V4h-1V3H6v1H5v2h3v2H1V1h2v3h1V2h2zM8 5h2v4h3v2H8z',
 settings:'M1 3h4V1h2v2h8v2H7v2H5V5H1zM1 8h8V6h2v2h4v2h-4v2H9v-2H1zM1 13h3v-2h2v2h9v2H6v1H4v-1H1z',
 search:'M3 1h6v1h2v2h1v5h-1v2H9v1H4v-1H2V9H1V4h1V2h1zM4 3v1H3v5h1v1h5V9h1V4H9V3zM11 11h2v1h1v1h1v1h1v2h-2v-1h-1v-1h-1v-1h-1z',
 swap:'M4 0h2v1h1v1h1v1h1v2H6v7H4V5H1V3h1V2h1V1h1zM10 4h2v7h3v2h-1v1h-1v1h-1v1h-2v-1H9v-1H8v-1H7v-2h3z'
}
</script>
<template><svg class="journey-control-icon" viewBox="0 0 16 16" shape-rendering="crispEdges" aria-hidden="true" focusable="false"><path :d="paths[props.kind]||paths.search" fill="currentColor" fill-rule="evenodd"/></svg></template>
<style scoped>.journey-control-icon{display:block;width:24px;height:24px;flex-shrink:0;image-rendering:pixelated}</style>

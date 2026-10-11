package de.ovgu.imiq.cricket

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Static controls share the map's existing vector palette and stroke style. */
@Composable fun TravelActionSprite(kind:String,modifier:Modifier=Modifier,highlighted:Boolean=false) {
 when(kind) {
  "origin","destination" -> Canvas(modifier.clearAndSetSemantics {}) {
   val rows=if(kind=="origin")listOf("010","101","111","101","101")else listOf("110","101","110","101","110")
   val unit=size.minDimension/7f
   val color=if(highlighted)Mint else Pale
   rows.forEachIndexed {y,row->row.forEachIndexed {x,c->if(c=='1')drawRoundRect(color,Offset((size.width-3*unit)/2+x*unit,(size.height-5*unit)/2+y*unit),Size(unit*.9f,unit*.9f),CornerRadius(unit*.1f))}}
  }
  "swap" -> Canvas(modifier.clearAndSetSemantics {}) {
   fun line(x:Float,y:Float,a:Float,b:Float,c:androidx.compose.ui.graphics.Color)=drawLine(c,Offset(size.width*x,size.height*y),Offset(size.width*a,size.height*b),2.dp.toPx(),StrokeCap.Round)
   line(.3f,.8f,.3f,.2f,Mint);line(.3f,.2f,.12f,.38f,Mint);line(.3f,.2f,.48f,.38f,Mint)
   line(.7f,.2f,.7f,.8f,Pale);line(.7f,.8f,.52f,.62f,Pale);line(.7f,.8f,.88f,.62f,Pale)
  }
  "preferences" -> PixelIcon("settings",modifier.clearAndSetSemantics {})
  else -> PixelIcon(kind,modifier.clearAndSetSemantics {})
 }
}

package com.securityresearch.os
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class Result(val name:String,val status:String,val detail:String)
private val BG=Color(0xFF070A10);private val SURFACE=Color(0xFF101722);private val GREEN=Color(0xFF65F6A4);private val CYAN=Color(0xFF56D9FF);private val RED=Color(0xFFFF647C);private val MUTED=Color(0xFF9AA8BA)

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{Scanner()}}}

@Composable fun Scanner(){
 var url by remember{mutableStateOf("")};var running by remember{mutableStateOf(false)};var results by remember{mutableStateOf(emptyList<Result>())};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
 MaterialTheme(colorScheme=darkColorScheme(background=BG,surface=SURFACE,primary=GREEN,secondary=CYAN,onBackground=Color.White,onSurface=Color.White)){
  Scaffold(containerColor=BG){pad->LazyColumn(Modifier.fillMaxSize().padding(pad).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{Text("BUG BOUNTY URL SCANNER",color=GREEN,fontWeight=FontWeight.Bold);Text("Authorized passive security checks",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Use only on targets you are explicitly authorized to assess.",color=MUTED)}
   item{OutlinedTextField(url,{url=it},label={Text("https://target.example")},modifier=Modifier.fillMaxWidth())}
   item{Button(enabled=!running&&(url.startsWith("https://")||url.startsWith("http://")),onClick={running=true;error="";scope.launch{try{results=withContext(Dispatchers.IO){check(url.trim())}}catch(e:Exception){error=e.message?:"Request failed"};running=false}},modifier=Modifier.fillMaxWidth()){Text(if(running)"CHECKING..." else "START SAFE CHECK")}}
   if(error.isNotBlank())item{Text(error,color=RED)}
   item{Text("RESULTS",color=CYAN,fontWeight=FontWeight.Bold)}
   items(results.size){i->val r=results[i];Card(colors=CardDefaults.cardColors(SURFACE)){Column(Modifier.padding(16.dp)){Text(r.status+"  "+r.name,color=if(r.status=="PASS")GREEN else RED,fontWeight=FontWeight.Bold);Text(r.detail,color=MUTED)}}}
   item{Text("Checks are indicators only, not proof that a target is vulnerable or secure.",color=MUTED)}
  }}
 }
}

private fun check(raw:String):List<Result>{
 val u=URL(raw);require(u.protocol=="http"||u.protocol=="https"){"HTTP(S) URL required"}
 val c=(u.openConnection() as HttpURLConnection).apply{requestMethod="GET";connectTimeout=8000;readTimeout=8000;instanceFollowRedirects=false;setRequestProperty("User-Agent","SecurityResearchOS/1.0")}
 val h=c.headerFields.mapKeys{it.key?.lowercase()?:""}.mapValues{it.value.joinToString("; ")};val out=mutableListOf<Result>()
 out+=Result("HTTP response","PASS","Received HTTP "+c.responseCode)
 out+=Result("HTTPS",if(u.protocol=="https")"PASS" else "REVIEW",if(u.protocol=="https")"HTTPS is in use." else "URL uses HTTP; verify HTTPS enforcement.")
 for(k in listOf("strict-transport-security","content-security-policy","x-content-type-options","referrer-policy")){val v=h[k];out+=Result(k.uppercase(),if(v.isNullOrBlank())"REVIEW" else "PASS",if(v.isNullOrBlank())"Header not observed." else v)}
 val cookie=h["set-cookie"].orEmpty();out+=Result("COOKIE FLAGS",if(cookie.isBlank()||cookie.contains("secure",true)&&cookie.contains("httponly",true)&&cookie.contains("samesite",true))"PASS" else "REVIEW",if(cookie.isBlank())"No Set-Cookie observed." else "Review Secure, HttpOnly and SameSite attributes.")
 val cors=h["access-control-allow-origin"];out+=Result("CORS",if(cors=="*")"REVIEW" else "PASS",cors?:"No ACAO header observed.")
 c.disconnect();return out
}
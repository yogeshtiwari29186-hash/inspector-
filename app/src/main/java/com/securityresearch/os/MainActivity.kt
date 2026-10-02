package com.securityresearch.os

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Bg=Color(0xFF090B10)
private val Card=Color(0xFF121620)
private val Accent=Color(0xFF7CFFB2)

data class Finding(val title:String,val severity:String,val target:String)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{App()}}
}

@Composable fun App(){
 var tab by remember{mutableStateOf(0)}
 val findings=remember{mutableStateListOf(
   Finding("Example authorization review","High","lab.example"),
   Finding("Security-header observation","Medium","demo.example")
 )}
 MaterialTheme(colorScheme=darkColorScheme(background=Bg,surface=Card,primary=Accent)){
  Scaffold(containerColor=Bg,bottomBar={
   NavigationBar(containerColor=Card){
    listOf("Home","Programs","Assets","Findings","Reports").forEachIndexed{i,s->
     NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text("•")},label={Text(s)})
    }
   }
  }){p->Box(Modifier.padding(p)){when(tab){
   0->Dashboard(findings.size)
   1->Programs()
   2->Assets()
   3->Findings(findings)
   else->Reports()
  }}}
 }
}
@Composable fun Header(title:String,sub:String){Column(Modifier.padding(20.dp)){Text(title,style=MaterialTheme.typography.headlineMedium);Text(sub,color=Color.LightGray)}}
@Composable fun Dashboard(count:Int){
 LazyColumn(Modifier.fillMaxSize()){
  item{Header("Security Research OS","Authorized bug-bounty workspace")}
  item{Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("Programs","0");Metric("Assets","0");Metric("Findings",count.toString())}}
  item{Section("Scope Guard","Every active workflow should pass authorization, scope and policy checks.")}
  item{Section("Research Pipeline","Program → Scope → Asset → Observation → Evidence → Finding → Report → Retest")}
  item{Section("AI Copilot","Evidence-based classification, CWE/OWASP mapping and report drafting workspace.")}
 }
}
@Composable fun Metric(a:String,b:String){Card(Modifier.width(105.dp).height(90.dp)){Column(Modifier.padding(12.dp)){Text(b,style=MaterialTheme.typography.headlineSmall);Text(a,color=Color.LightGray)}}}
@Composable fun Section(t:String,b:String){Card(Modifier.fillMaxWidth().padding(16.dp,8.dp)){Column(Modifier.padding(16.dp)){Text(t,style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(6.dp));Text(b,color=Color.LightGray)}}}
@Composable fun Programs(){LazyColumn{item{Header("Programs","Define bounty programs and their explicit scope.")};item{Section("No programs yet","Add a program with in-scope assets, exclusions, rate limits and research rules.")};item{Button(onClick={},modifier=Modifier.padding(16.dp)){Text("Add Program")}}}}
@Composable fun Assets(){LazyColumn{item{Header("Assets","Authorized domains, APIs, applications and endpoints.")};item{Section("Asset inventory","Import or add assets only when you have permission to assess them.")};item{Button(onClick={},modifier=Modifier.padding(16.dp)){Text("Add Asset")}}}}
@Composable fun Findings(list:List<Finding>){LazyColumn{item{Header("Findings","Evidence-backed vulnerability cases.")};items(list){f->Section(f.title,f.severity+" • "+f.target)};item{Button(onClick={},modifier=Modifier.padding(16.dp)){Text("New Finding")}}}}
@Composable fun Reports(){LazyColumn{item{Header("Reports","Build professional, evidence-based submissions.")};item{Section("Report Builder","Summary • technical details • reproduction • impact • remediation • references")};item{Button(onClick={},modifier=Modifier.padding(16.dp)){Text("Create Report")}}}}

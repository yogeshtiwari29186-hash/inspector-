package com.securityresearch.os

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Bg=Color(0xFF070A10); private val Surface=Color(0xFF101722); private val Surface2=Color(0xFF151E2B)
private val Green=Color(0xFF65F6A4); private val Cyan=Color(0xFF56D9FF); private val Purple=Color(0xFF9B8CFF)
private val Red=Color(0xFFFF647C); private val Amber=Color(0xFFFFC857); private val Muted=Color(0xFF9AA8BA)

data class Program(val id:String,val name:String,val scope:String,val rules:String)
data class Asset(val id:String,val value:String,val type:String,val program:String)
data class Finding(val id:String,val title:String,val severity:String,val target:String,val status:String,val evidence:String)
data class Note(val id:String,val title:String,val body:String)

class Store(ctx:Context){
 private val p=ctx.getSharedPreferences("security_os",0)
 private fun arr(k:String)=JSONArray(p.getString(k,"[]")?:"[]")
 fun programs()=runList("p"){o->Program(o.getString("id"),o.getString("name"),o.getString("scope"),o.getString("rules"))}
 fun assets()=runList("a"){o->Asset(o.getString("id"),o.getString("value"),o.getString("type"),o.getString("program"))}
 fun findings()=runList("f"){o->Finding(o.getString("id"),o.getString("title"),o.getString("severity"),o.getString("target"),o.getString("status"),o.getString("evidence"))}
 fun notes()=runList("n"){o->Note(o.getString("id"),o.getString("title"),o.getString("body"))}
 private inline fun <T> runList(k:String,map:(JSONObject)->T):List<T>{val a=arr(k);return (0 until a.length()).map{map(a.getJSONObject(it))}}
 private fun put(k:String,o:JSONObject){val a=arr(k);a.put(o);p.edit().putString(k,a.toString()).apply()}
 fun add(x:Program)=put("p",JSONObject().apply{put("id",x.id);put("name",x.name);put("scope",x.scope);put("rules",x.rules)})
 fun add(x:Asset)=put("a",JSONObject().apply{put("id",x.id);put("value",x.value);put("type",x.type);put("program",x.program)})
 fun add(x:Finding)=put("f",JSONObject().apply{put("id",x.id);put("title",x.title);put("severity",x.severity);put("target",x.target);put("status",x.status);put("evidence",x.evidence)})
 fun add(x:Note)=put("n",JSONObject().apply{put("id",x.id);put("title",x.title);put("body",x.body)})
}

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{App(Store(this))}}}

@Composable fun App(store:Store){
 var tab by remember{mutableIntStateOf(0)}; var tick by remember{mutableIntStateOf(0)}
 val programs=remember(tick){store.programs()}; val assets=remember(tick){store.assets()}; val findings=remember(tick){store.findings()}; val notes=remember(tick){store.notes()}
 MaterialTheme(colorScheme=darkColorScheme(background=Bg,surface=Surface,primary=Green,secondary=Cyan,onBackground=Color.White,onSurface=Color.White)){
  Scaffold(containerColor=Bg,bottomBar={NavigationBar(containerColor=Surface){
   listOf("Overview","Programs","Assets","Findings","Reports").forEachIndexed{i,n->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","◈","◎","◆","▣")[i],color=if(tab==i)Green else Muted)},label={Text(n)})}
  }}){pad->Box(Modifier.padding(pad)){when(tab){
   0->Dashboard(programs,assets,findings,notes){tab=3}
   1->ProgramsScreen(programs){store.add(it);tick++}
   2->AssetsScreen(assets,programs){store.add(it);tick++}
   3->FindingsScreen(findings){store.add(it);tick++}
   else->ReportsScreen(findings)
  }}}
 }
}

@Composable fun Header(title:String,subtitle:String){Column(Modifier.padding(20.dp)){Text("SECURITY RESEARCH OS",color=Green,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(subtitle,color=Muted)}}
@Composable fun Stat(title:String,value:String,accent:Color,modifier:Modifier){Card(modifier,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(Surface2)){Column(Modifier.padding(15.dp)){Text(value,color=accent,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(title,color=Muted)}}}
@Composable fun Panel(title:String,body:String,accent:Color=Green){Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(Surface)){Column(Modifier.padding(18.dp)){Text(title,color=accent,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Spacer(Modifier.height(7.dp));Text(body,color=Muted)}}}

@Composable fun Dashboard(p:List<Program>,a:List<Asset>,f:List<Finding>,notes:List<Note>,openFindings:()->Unit){
 LazyColumn(Modifier.fillMaxSize()){
  item{Header("Research Command Center","Professional workspace for authorized bug-bounty research")}
  item{Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("Programs",p.size.toString(),Green,Modifier.weight(1f));Stat("Assets",a.size.toString(),Cyan,Modifier.weight(1f));Stat("Findings",f.size.toString(),Purple,Modifier.weight(1f))}}
  item{Spacer(Modifier.height(6.dp));Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("Open",f.count{it.status!="Closed"}.toString(),Amber,Modifier.weight(1f));Stat("Notes",notes.size.toString(),Cyan,Modifier.weight(1f))}}
  item{Panel("● SCOPE GUARD","Authorization → Program → Asset → Research. Out-of-scope assets should never enter an active workflow.",Green)}
  item{Panel("RESEARCH PIPELINE","Asset inventory → passive observation → evidence → finding → report → retest. Keep every observation reproducible.",Cyan)}
  item{Panel("HTTP WORKBENCH","Safe workspace for storing and reviewing authorized request/response samples. No credential harvesting or uncontrolled exploitation.",Purple)}
  item{Panel("AI COPILOT","Planned evidence-first assistance: explain observations, map CWE/OWASP categories and draft reports without inventing evidence.",Purple)}
  item{Panel("EVIDENCE CHAIN","Attach notes, timestamps and reproduction details to each finding so a submission can be independently reviewed.",Green)}
  if(f.isNotEmpty())item{Button(onClick=openFindings,modifier=Modifier.padding(16.dp).fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("OPEN FINDINGS QUEUE")}}
  item{Panel("SAFETY","Only assess targets you are explicitly authorized to test. Credential theft, password cracking, login bypass and uncontrolled exploit automation are excluded.",Red)}
 }
}

@Composable fun ProgramsScreen(list:List<Program>,add:(Program)->Unit){var open by remember{mutableStateOf(false)};LazyColumn{item{Header("Programs","Scope, exclusions, rules and research boundaries")};if(list.isEmpty())item{Panel("NO PROGRAMS","Create an authorized program before registering assets.",Amber)};items(list){x->Panel(x.name,"SCOPE\n"+x.scope+"\n\nRULES / EXCLUSIONS\n"+x.rules.ifBlank{"None defined."},Green))};item{Button(onClick={open=true},modifier=Modifier.padding(16.dp).fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("+ CREATE PROGRAM")}}};if(open)ProgramDialog({open=false},add)}
@Composable fun ProgramDialog(close:()->Unit,add:(Program)->Unit){var n by remember{mutableStateOf("")};var s by remember{mutableStateOf("")};var r by remember{mutableStateOf("")};DialogShell("Create Program",close,n.isNotBlank()&&s.isNotBlank(),{add(Program(UUID.randomUUID().toString(),n,s,r));close()}){Field("Program name",n){n=it};Field("Authorized scope",s){s=it};Field("Rules / exclusions",r){r=it}}}

@Composable fun AssetsScreen(list:List<Asset>,programs:List<Program>,add:(Asset)->Unit){var open by remember{mutableStateOf(false)};LazyColumn{item{Header("Asset Inventory","Domains, URLs, APIs and applications inside declared scope")};if(list.isEmpty())item{Panel("NO ASSETS","Create a program, then register an explicitly authorized asset.",Amber)};items(list){x->Panel(x.value,x.type+"  •  "+x.program+"  •  AUTHORIZED",Cyan))};item{Button(onClick={open=true},enabled=programs.isNotEmpty(),modifier=Modifier.padding(16.dp).fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("+ REGISTER AUTHORIZED ASSET")}}};if(open)AssetDialog({open=false},programs,add)}
@Composable fun AssetDialog(close:()->Unit,programs:List<Program>,add:(Asset)->Unit){var v by remember{mutableStateOf("")};var t by remember{mutableStateOf("Domain")};var p by remember{mutableStateOf(programs.firstOrNull()?.name?:"")};DialogShell("Register Asset",close,v.isNotBlank()&&p.isNotBlank(),{add(Asset(UUID.randomUUID().toString(),v,t,p));close()}){Field("Domain / URL / API",v){v=it};Field("Asset type",t){t=it};Field("Program",p){p=it};Text("Only register assets you are authorized to assess.",color=Muted)}}

@Composable fun FindingsScreen(list:List<Finding>,add:(Finding)->Unit){var open by remember{mutableStateOf(false)};LazyColumn{item{Header("Findings","Evidence-backed observations from authorized research")};if(list.isEmpty())item{Panel("NO FINDINGS","Create a structured observation when you have evidence.",Amber)};items(list){x->val c=when(x.severity){"Critical"->Red;"High"->Amber;"Low"->Cyan;else->Purple};Panel(x.title,x.severity.uppercase()+"  •  "+x.status+"\nTarget: "+x.target+"\nEvidence: "+x.evidence.ifBlank{"Not attached"},c)};item{Button(onClick={open=true},modifier=Modifier.padding(16.dp).fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("+ NEW FINDING")}}};if(open)FindingDialog({open=false},add)}
@Composable fun FindingDialog(close:()->Unit,add:(Finding)->Unit){var t by remember{mutableStateOf("")};var sev by remember{mutableStateOf("Medium")};var target by remember{mutableStateOf("")};var ev by remember{mutableStateOf("")};DialogShell("New Finding",close,t.isNotBlank()&&target.isNotBlank(),{add(Finding(UUID.randomUUID().toString(),t,sev,target,"Draft",ev));close()}){Field("Finding title",t){t=it};Field("Severity (Critical / High / Medium / Low)",sev){sev=it};Field("Authorized target",target){target=it};Field("Evidence / observation",ev){ev=it}}}

@Composable fun ReportsScreen(list:List<Finding>){LazyColumn{item{Header("Report Studio","Structured bug-bounty submission drafts")};item{Panel("REPORT WORKFLOW","Finding → Summary → Technical details → Reproduction → Impact → Remediation → References",Purple)};item{Panel("QUALITY GATE","A report should contain evidence, an authorized target, reproducible steps, impact and a clear remediation.",Green)};if(list.isEmpty())item{Panel("NOTHING TO REPORT","Create a finding first.",Amber)};items(list){x->Panel("DRAFT • "+x.title,"Summary: "+x.title+"\nTarget: "+x.target+"\nSeverity: "+x.severity+"\nStatus: "+x.status+"\n\nEvidence: "+x.evidence,Purple)}}}

@Composable fun Field(label:String,value:String,onChange:(String)->Unit){OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Green,focusedLabelColor=Green,cursorColor=Green))}
@Composable fun DialogShell(title:String,close:()->Unit,enabled:Boolean,confirm:()->Unit,content:@Composable ColumnScope.()->Unit){AlertDialog(onDismissRequest=close,title={Text(title,fontWeight=FontWeight.Bold)},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp),content=content)},confirmButton={Button(onClick=confirm,enabled=enabled){Text("SAVE")}},dismissButton={TextButton(onClick=close){Text("CANCEL")}})}

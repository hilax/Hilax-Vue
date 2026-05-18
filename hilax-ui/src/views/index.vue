<template>
  <div class="app-container home">
    <section class="hero">
      <div>
        <h1>速卖通运费管理系统</h1>
        <p>用于维护速卖通 POP 模式运费模板、区域报价和店小秘表格复制数据。</p>
      </div>
      <el-button type="primary" icon="el-icon-s-operation" @click="goRegionalPrice">
        打开区域报价
      </el-button>
    </section>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never">
          <div class="card-title">运费模板</div>
          <p>支持无忧物流-标准、无忧物流-特货标准、无忧物流-陆运大件专线。</p>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never">
          <div class="card-title">区域报价</div>
          <p>按国家或地区计算售价，并生成可复制到店小秘的表格内容。</p>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never">
          <div class="card-title">本地模板</div>
          <p>默认读取服务器保存的模板，需要更新时再展开上传区域。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
export default {
  name: 'Index',
  methods: {
    goRegionalPrice() {
      this.$router.push('/system/regionalPrice')
    }
  }
}
</script>

<style scoped lang="scss">
.home {
  color: #263445;
}

.hero {
  min-height: 180px;
  margin-bottom: 16px;
  padding: 28px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  background: linear-gradient(135deg, #f5f8fb 0%, #eef6f1 100%);
  border: 1px solid #e5e9f2;
  border-radius: 8px;
}

.hero h1 {
  margin: 0 0 12px;
  font-size: 28px;
  line-height: 1.3;
  font-weight: 700;
}

.hero p {
  margin: 0;
  font-size: 15px;
  color: #667085;
}

.card-title {
  margin-bottom: 10px;
  font-size: 16px;
  font-weight: 700;
}

.el-card p {
  margin: 0;
  min-height: 48px;
  color: #667085;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .hero {
    align-items: flex-start;
    flex-direction: column;
    padding: 22px;
  }
}
</style>
